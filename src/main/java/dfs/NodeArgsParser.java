package dfs;

/**
 * Parser pentru argumentele din linia de comandă ale unui nod.
 */
public class NodeArgsParser {

    public static NodeConfig parse(String[] args) {
        String id = null;
        String portStr = null;
        String dir = null;
        String teamStr = null;
        boolean coordinator = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--id":
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        throw new IllegalArgumentException("Lipsește valoarea pentru argumentul --id.");
                    }
                    id = args[++i].trim();
                    break;
                case "--port":
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        throw new IllegalArgumentException("Lipsește valoarea pentru argumentul --port.");
                    }
                    portStr = args[++i].trim();
                    break;
                case "--dir":
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        throw new IllegalArgumentException("Lipsește valoarea pentru argumentul --dir.");
                    }
                    dir = args[++i].trim();
                    break;
                case "--echipa":
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        throw new IllegalArgumentException("Lipsește valoarea pentru argumentul --echipa.");
                    }
                    teamStr = args[++i].trim();
                    break;
                case "--coordinator":
                    coordinator = true;
                    break;
                default:
                    throw new IllegalArgumentException("Argument necunoscut: " + arg);
            }
        }

        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Argumentul obligatoriu --id lipsește.");
        }
        if (portStr == null || portStr.isEmpty()) {
            throw new IllegalArgumentException("Argumentul obligatoriu --port lipsește.");
        }
        if (dir == null || dir.isEmpty()) {
            throw new IllegalArgumentException("Argumentul obligatoriu --dir lipsește.");
        }
        if (teamStr == null || teamStr.isEmpty()) {
            throw new IllegalArgumentException("Argumentul obligatoriu --echipa lipsește.");
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port invalid: '" + portStr + "'. Portul trebuie să fie un număr întreg.");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port invalid: " + port + ". Portul trebuie să fie între 1 și 65535.");
        }

        int team;
        try {
            team = Integer.parseInt(teamStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Număr de echipă invalid: '" + teamStr + "'. Trebuie să fie un număr întreg.");
        }

        if (team < 1 || team > 254) {
            throw new IllegalArgumentException("Număr de echipă invalid: " + team + ". Trebuie să fie între 1 și 254.");
        }

        return new NodeConfig(id, port, dir, team, coordinator);
    }
}
