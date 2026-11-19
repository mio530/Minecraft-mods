package de.mio.ryzeteams.storage;

import de.mio.ryzeteams.model.DefaultRoles;
import de.mio.ryzeteams.model.Invite;
import de.mio.ryzeteams.model.Member;
import de.mio.ryzeteams.model.Permission;
import de.mio.ryzeteams.model.Team;
import de.mio.ryzeteams.model.TeamHome;
import de.mio.ryzeteams.model.TeamRole;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SQLite-backed {@link TeamRepository}. A single connection is guarded by this
 * object's monitor; every public method is synchronized, and the service layer
 * only calls it off the main thread. WAL journaling plus {@code synchronous=
 * NORMAL} gives durable, crash-safe writes without blocking readers.
 *
 * <p>Schema evolution is handled by {@link #migrate()} against a
 * {@code schema_version} row, so future versions can add migrations in order.
 */
public final class SqliteTeamRepository implements TeamRepository {

    private static final int SCHEMA_VERSION = 1;

    private final Path databaseFile;
    private Connection connection;

    public SqliteTeamRepository(Path databaseFile) {
        this.databaseFile = databaseFile;
    }

    @Override
    public synchronized void initialize() {
        try {
            // Force driver registration from this (plugin) classloader; sqlite-jdbc
            // is supplied at runtime via plugin.yml `libraries`.
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new StorageException("SQLite JDBC driver not found on classpath", e);
        }
        try {
            if (databaseFile.getParent() != null) {
                java.nio.file.Files.createDirectories(databaseFile.getParent());
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA journal_mode = WAL");
                st.execute("PRAGMA synchronous = NORMAL");
                st.execute("PRAGMA busy_timeout = 5000");
            }
            migrate();
        } catch (Exception e) {
            throw new StorageException("Failed to initialize SQLite database", e);
        }
    }

    private void migrate() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
        }
        int current = 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            if (rs.next()) {
                current = rs.getInt(1);
            }
        }
        boolean fresh = current == 0;
        connection.setAutoCommit(false);
        try {
            if (current < 1) {
                applyV1();
            }
            // Future migrations: if (current < 2) { applyV2(); }
            try (Statement st = connection.createStatement()) {
                if (fresh) {
                    st.execute("DELETE FROM schema_version");
                    st.execute("INSERT INTO schema_version(version) VALUES (" + SCHEMA_VERSION + ")");
                } else if (current < SCHEMA_VERSION) {
                    st.execute("UPDATE schema_version SET version = " + SCHEMA_VERSION);
                }
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void applyV1() throws SQLException {
        String[] ddl = {
                """
                CREATE TABLE teams (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    name_lower TEXT NOT NULL UNIQUE,
                    owner TEXT NOT NULL,
                    default_role TEXT NOT NULL,
                    home_world TEXT, home_x REAL, home_y REAL, home_z REAL,
                    home_yaw REAL, home_pitch REAL,
                    created_at INTEGER NOT NULL
                )""",
                """
                CREATE TABLE members (
                    team_id TEXT NOT NULL,
                    uuid TEXT NOT NULL,
                    name TEXT,
                    role_id TEXT NOT NULL,
                    joined_at INTEGER NOT NULL,
                    PRIMARY KEY (team_id, uuid),
                    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
                )""",
                "CREATE INDEX idx_members_uuid ON members(uuid)",
                """
                CREATE TABLE roles (
                    team_id TEXT NOT NULL,
                    role_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    rank INTEGER NOT NULL,
                    PRIMARY KEY (team_id, role_id),
                    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
                )""",
                """
                CREATE TABLE role_permissions (
                    team_id TEXT NOT NULL,
                    role_id TEXT NOT NULL,
                    permission TEXT NOT NULL,
                    PRIMARY KEY (team_id, role_id, permission),
                    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
                )""",
                """
                CREATE TABLE alliances (
                    team_a TEXT NOT NULL,
                    team_b TEXT NOT NULL,
                    PRIMARY KEY (team_a, team_b)
                )""",
                """
                CREATE TABLE alliance_requests (
                    from_team TEXT NOT NULL,
                    to_team TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    PRIMARY KEY (from_team, to_team)
                )""",
                """
                CREATE TABLE invites (
                    team_id TEXT NOT NULL,
                    invitee TEXT NOT NULL,
                    inviter TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    PRIMARY KEY (team_id, invitee)
                )""",
                """
                CREATE TABLE enderchest (
                    team_id TEXT NOT NULL,
                    slot INTEGER NOT NULL,
                    item BLOB NOT NULL,
                    PRIMARY KEY (team_id, slot),
                    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
                )"""
        };
        try (Statement st = connection.createStatement()) {
            for (String sql : ddl) {
                st.execute(sql);
            }
        }
    }

    // ---- teams --------------------------------------------------------------

    @Override
    public synchronized List<Team> loadAllTeams() {
        Map<UUID, Team> byId = new LinkedHashMap<>();
        try {
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM teams")) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("id"));
                    Team team = new Team(id, rs.getString("name"), UUID.fromString(rs.getString("owner")));
                    String world = rs.getString("home_world");
                    if (world != null) {
                        team.setHome(new TeamHome(world,
                                rs.getDouble("home_x"), rs.getDouble("home_y"), rs.getDouble("home_z"),
                                rs.getFloat("home_yaw"), rs.getFloat("home_pitch")));
                    }
                    byId.put(id, team);
                }
            }
            // roles + permissions
            Map<String, EnumSet<Permission>> permsByRole = new HashMap<>();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM role_permissions")) {
                while (rs.next()) {
                    String key = rs.getString("team_id") + "|" + rs.getString("role_id");
                    Permission.byName(rs.getString("permission")).ifPresent(p ->
                            permsByRole.computeIfAbsent(key, k -> EnumSet.noneOf(Permission.class)).add(p));
                }
            }
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM roles")) {
                while (rs.next()) {
                    Team team = byId.get(UUID.fromString(rs.getString("team_id")));
                    if (team == null) {
                        continue;
                    }
                    String roleId = rs.getString("role_id");
                    EnumSet<Permission> perms = permsByRole.getOrDefault(
                            rs.getString("team_id") + "|" + roleId, EnumSet.noneOf(Permission.class));
                    team.putRole(new TeamRole(roleId, rs.getString("name"), rs.getInt("rank"), perms));
                }
            }
            // default role + safety: ensure every team has the five defaults present
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, default_role FROM teams")) {
                while (rs.next()) {
                    Team team = byId.get(UUID.fromString(rs.getString("id")));
                    if (team != null) {
                        if (team.roles().isEmpty()) {
                            DefaultRoles.apply(team);
                        }
                        team.setDefaultRoleId(rs.getString("default_role"));
                    }
                }
            }
            // members
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM members")) {
                while (rs.next()) {
                    Team team = byId.get(UUID.fromString(rs.getString("team_id")));
                    if (team != null) {
                        team.addMember(new Member(UUID.fromString(rs.getString("uuid")),
                                rs.getString("name"), rs.getString("role_id"), rs.getLong("joined_at")));
                    }
                }
            }
            // alliances (undirected)
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM alliances")) {
                while (rs.next()) {
                    UUID a = UUID.fromString(rs.getString("team_a"));
                    UUID b = UUID.fromString(rs.getString("team_b"));
                    Team ta = byId.get(a);
                    Team tb = byId.get(b);
                    if (ta != null) {
                        ta.addAlly(b);
                    }
                    if (tb != null) {
                        tb.addAlly(a);
                    }
                }
            }
            // alliance requests (directed)
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM alliance_requests")) {
                while (rs.next()) {
                    UUID from = UUID.fromString(rs.getString("from_team"));
                    UUID to = UUID.fromString(rs.getString("to_team"));
                    Team tf = byId.get(from);
                    Team tt = byId.get(to);
                    if (tf != null) {
                        tf.addOutgoingAllianceRequest(to);
                    }
                    if (tt != null) {
                        tt.addIncomingAllianceRequest(from);
                    }
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load teams", e);
        }
        return new ArrayList<>(byId.values());
    }

    @Override
    public synchronized void saveTeam(Team team) {
        try {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO teams(id,name,name_lower,owner,default_role,
                            home_world,home_x,home_y,home_z,home_yaw,home_pitch,created_at)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                        ON CONFLICT(id) DO UPDATE SET
                            name=excluded.name, name_lower=excluded.name_lower, owner=excluded.owner,
                            default_role=excluded.default_role, home_world=excluded.home_world,
                            home_x=excluded.home_x, home_y=excluded.home_y, home_z=excluded.home_z,
                            home_yaw=excluded.home_yaw, home_pitch=excluded.home_pitch
                        """)) {
                    ps.setString(1, team.id().toString());
                    ps.setString(2, team.name());
                    ps.setString(3, team.name().toLowerCase(java.util.Locale.ROOT));
                    ps.setString(4, team.owner().toString());
                    ps.setString(5, team.defaultRoleId());
                    TeamHome h = team.home().orElse(null);
                    if (h != null) {
                        ps.setString(6, h.world());
                        ps.setDouble(7, h.x());
                        ps.setDouble(8, h.y());
                        ps.setDouble(9, h.z());
                        ps.setFloat(10, h.yaw());
                        ps.setFloat(11, h.pitch());
                    } else {
                        ps.setNull(6, java.sql.Types.VARCHAR);
                        ps.setNull(7, java.sql.Types.REAL);
                        ps.setNull(8, java.sql.Types.REAL);
                        ps.setNull(9, java.sql.Types.REAL);
                        ps.setNull(10, java.sql.Types.REAL);
                        ps.setNull(11, java.sql.Types.REAL);
                    }
                    ps.setLong(12, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                replaceRoles(team);
                replaceMembers(team);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to save team " + team.id(), e);
        }
    }

    private void replaceRoles(Team team) throws SQLException {
        try (PreparedStatement del = connection.prepareStatement("DELETE FROM roles WHERE team_id=?");
             PreparedStatement delP = connection.prepareStatement("DELETE FROM role_permissions WHERE team_id=?")) {
            del.setString(1, team.id().toString());
            del.executeUpdate();
            delP.setString(1, team.id().toString());
            delP.executeUpdate();
        }
        try (PreparedStatement ins = connection.prepareStatement(
                "INSERT INTO roles(team_id,role_id,name,rank) VALUES(?,?,?,?)");
             PreparedStatement insP = connection.prepareStatement(
                     "INSERT INTO role_permissions(team_id,role_id,permission) VALUES(?,?,?)")) {
            for (TeamRole role : team.roles()) {
                ins.setString(1, team.id().toString());
                ins.setString(2, role.id());
                ins.setString(3, role.name());
                ins.setInt(4, role.rank());
                ins.addBatch();
                for (Permission p : role.permissions()) {
                    insP.setString(1, team.id().toString());
                    insP.setString(2, role.id());
                    insP.setString(3, p.name());
                    insP.addBatch();
                }
            }
            ins.executeBatch();
            insP.executeBatch();
        }
    }

    private void replaceMembers(Team team) throws SQLException {
        try (PreparedStatement del = connection.prepareStatement("DELETE FROM members WHERE team_id=?")) {
            del.setString(1, team.id().toString());
            del.executeUpdate();
        }
        try (PreparedStatement ins = connection.prepareStatement(
                "INSERT INTO members(team_id,uuid,name,role_id,joined_at) VALUES(?,?,?,?,?)")) {
            for (Member m : team.members()) {
                ins.setString(1, team.id().toString());
                ins.setString(2, m.uuid().toString());
                ins.setString(3, m.lastKnownName());
                ins.setString(4, m.roleId());
                ins.setLong(5, m.joinedAt());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    @Override
    public synchronized void deleteTeam(UUID teamId) {
        try {
            connection.setAutoCommit(false);
            try {
                exec("DELETE FROM enderchest WHERE team_id=?", teamId.toString());
                exec("DELETE FROM role_permissions WHERE team_id=?", teamId.toString());
                exec("DELETE FROM roles WHERE team_id=?", teamId.toString());
                exec("DELETE FROM members WHERE team_id=?", teamId.toString());
                exec("DELETE FROM invites WHERE team_id=?", teamId.toString());
                exec("DELETE FROM alliances WHERE team_a=? OR team_b=?", teamId.toString(), teamId.toString());
                exec("DELETE FROM alliance_requests WHERE from_team=? OR to_team=?", teamId.toString(), teamId.toString());
                exec("DELETE FROM teams WHERE id=?", teamId.toString());
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to delete team " + teamId, e);
        }
    }

    // ---- enderchest ---------------------------------------------------------

    @Override
    public synchronized Map<Integer, byte[]> loadEnderchest(UUID teamId) {
        Map<Integer, byte[]> out = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT slot, item FROM enderchest WHERE team_id=?")) {
            ps.setString(1, teamId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getInt("slot"), rs.getBytes("item"));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load enderchest " + teamId, e);
        }
        return out;
    }

    @Override
    public synchronized void saveEnderchest(UUID teamId, Map<Integer, byte[]> slots) {
        try {
            connection.setAutoCommit(false);
            try {
                exec("DELETE FROM enderchest WHERE team_id=?", teamId.toString());
                try (PreparedStatement ins = connection.prepareStatement(
                        "INSERT INTO enderchest(team_id,slot,item) VALUES(?,?,?)")) {
                    for (Map.Entry<Integer, byte[]> e : slots.entrySet()) {
                        if (e.getValue() == null) {
                            continue;
                        }
                        ins.setString(1, teamId.toString());
                        ins.setInt(2, e.getKey());
                        ins.setBytes(3, e.getValue());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to save enderchest " + teamId, e);
        }
    }

    // ---- invites ------------------------------------------------------------

    @Override
    public synchronized List<Invite> loadInvites() {
        List<Invite> out = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM invites")) {
            while (rs.next()) {
                out.add(new Invite(
                        UUID.fromString(rs.getString("team_id")),
                        UUID.fromString(rs.getString("invitee")),
                        UUID.fromString(rs.getString("inviter")),
                        rs.getLong("created_at")));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load invites", e);
        }
        return out;
    }

    @Override
    public synchronized void saveInvite(Invite invite) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO invites(team_id,invitee,inviter,created_at) VALUES(?,?,?,?)
                ON CONFLICT(team_id,invitee) DO UPDATE SET inviter=excluded.inviter, created_at=excluded.created_at
                """)) {
            ps.setString(1, invite.teamId().toString());
            ps.setString(2, invite.invitee().toString());
            ps.setString(3, invite.inviter().toString());
            ps.setLong(4, invite.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to save invite", e);
        }
    }

    @Override
    public synchronized void deleteInvite(UUID teamId, UUID invitee) {
        try {
            exec("DELETE FROM invites WHERE team_id=? AND invitee=?", teamId.toString(), invitee.toString());
        } catch (SQLException e) {
            throw new StorageException("Failed to delete invite", e);
        }
    }

    @Override
    public synchronized void deleteInvitesForInvitee(UUID invitee) {
        try {
            exec("DELETE FROM invites WHERE invitee=?", invitee.toString());
        } catch (SQLException e) {
            throw new StorageException("Failed to delete invites for invitee", e);
        }
    }

    // ---- alliances ----------------------------------------------------------

    @Override
    public synchronized void addAlliance(UUID a, UUID b) {
        String lo = min(a, b);
        String hi = max(a, b);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO alliances(team_a,team_b) VALUES(?,?)")) {
            ps.setString(1, lo);
            ps.setString(2, hi);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to add alliance", e);
        }
    }

    @Override
    public synchronized void removeAlliance(UUID a, UUID b) {
        try {
            exec("DELETE FROM alliances WHERE team_a=? AND team_b=?", min(a, b), max(a, b));
        } catch (SQLException e) {
            throw new StorageException("Failed to remove alliance", e);
        }
    }

    @Override
    public synchronized void addAllianceRequest(UUID from, UUID to, long createdAt) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO alliance_requests(from_team,to_team,created_at) VALUES(?,?,?)")) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            ps.setLong(3, createdAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to add alliance request", e);
        }
    }

    @Override
    public synchronized void removeAllianceRequest(UUID from, UUID to) {
        try {
            exec("DELETE FROM alliance_requests WHERE from_team=? AND to_team=?", from.toString(), to.toString());
        } catch (SQLException e) {
            throw new StorageException("Failed to remove alliance request", e);
        }
    }

    // ---- helpers ------------------------------------------------------------

    private void exec(String sql, String... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }

    private static String min(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) <= 0 ? a.toString() : b.toString();
    }

    private static String max(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) > 0 ? a.toString() : b.toString();
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // closing on shutdown; nothing actionable
            }
        }
    }
}
