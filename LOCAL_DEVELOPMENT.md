# Local Development Environment

The repository contains two local runtime styles:

- `run/server` is the normal ForgeGradle development runtime.
- `run/arclight-server` is the prod-like hybrid Arclight/Forge runtime used for testing against the real server mod and plugin set.

The prod-like environment currently targets Minecraft `1.20.1`, Forge `47.4.18`, and Arclight `1.20.1-1.0.6-SNAPSHOT`. The Arclight server is started with Java 21 because the current plugin set includes Java 21 plugins. PortableMC clients are started with Java 17 and Forge `1.20.1-47.4.18`.

Expected local prod-like layout:

```text
local-runtime/arclight/server.jar
run/arclight-server/mods
run/arclight-server/config
run/arclight-server/plugins
run/arclight-server/server.properties
run/portablemc/main
run/portablemc/main_client
run/portablemc/off_client
```

`run/arclight-server/mods`, `run/arclight-server/config`, and `run/arclight-server/plugins` should mirror the real server when reproducing production behavior. The PortableMC setup script copies the server `mods` and `config` folders into both test client work directories, then replaces the PvPRating jar with the freshly built local jar.

Current prod-like world generation is configured in `run/arclight-server/server.properties`:

```properties
level-seed=-8542766361641119311
level-type=tfc\:overworld
```

To regenerate the test world, stop the server, rename or delete `run/arclight-server/world`, keep the seed and `tfc\:overworld` preset in `server.properties`, then start the Arclight server again. Renaming the old world is safer than deleting it.

Useful VS Code tasks:

| Task | What it does |
| --- | --- |
| `Prod: Setup PortableMC Clients` | Creates/updates the local PortableMC venv, builds PvPRating, and prepares `main_client` and `off_client`. |
| `Prod: Arclight Server` | Builds PvPRating, updates the Arclight server mod jar, prepares Towny dev plugins, and starts the Arclight server. |
| `Prod: Main PortableMC Client` | Starts the first offline test client as `MainDev` and connects it to `127.0.0.1:25565`. |
| `Prod: Off PortableMC Client` | Starts the second offline test client as `OffDev` and connects it to `127.0.0.1:25565`. |
| `Prod: Start PortableMC PvP Stack` | Runs setup first, then starts the Arclight server and both PortableMC clients in VS Code task panels. |

The active launcher path is PortableMC. Prism Launcher scripts were removed because Prism repeatedly required Microsoft account reauthentication in this offline test setup.
