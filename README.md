# Go Debug

Run and debug Go programs and tests in the free IntelliJ IDEA (2026.2+), using
[Delve](https://github.com/go-delve/delve) through IntelliJ's built-in Debug Adapter Protocol (DAP) client.

- **Breakpoints**: click the gutter in any `.go` file; conditions and hit counts work
- **Debugger**: step over/into/out, call stacks per goroutine, variables, watches and *Evaluate Expression*; stops automatically on unrecovered panics
- **Go run configuration**: package or tests, test pattern, program arguments, build flags (`-race`, `-tags=…`), working directory, environment variables
- **Right-click a `.go` file → Run / Debug Go Package**: creates or reuses a run configuration for that directory; in a `_test.go` file it runs the package's tests

**Run** uses `go run` / `go test -v`. **Debug** starts `dlv dap` on a free local port and the IDE drives it over DAP.
Program output appears in the Debug tool window's console.

## Requirements

- IntelliJ IDEA 2026.2 or newer (the free tier is fine)
- Go, plus Delve: `go install github.com/go-delve/delve/cmd/dlv@latest`

`go` and `dlv` are found via `PATH`, `GOROOT/bin`, `GOBIN`, `GOPATH/bin`, `~/go/bin` and the standard
install locations, so a shell-only `PATH` isn't needed. Go's `bin` directory is put first on `PATH` for
`dlv`, so it builds with the same toolchain.

Pairs well with **Go Diagnostics** (inline errors) and **Go New Project** (project wizard).

## Notes

- IntelliJ's DAP API is marked experimental in 2026.2, so a future IDE release may need an update.
- `.go` files are highlighted by the bundled TextMate grammar and have no Go PSI, so there are no gutter
  run icons; use the right-click actions or a run configuration.
- When JetBrains' Go plugin is active it owns `.go` breakpoints, and this plugin's breakpoint type steps aside.

## Build and install

```sh
./gradlew test buildPlugin
```

Then *Settings | Plugins | ⚙ | Install Plugin from Disk…* and pick `build/distributions/go-debug-0.1.0.zip`.

## Layout

| File | Purpose |
|---|---|
| `Delve.kt` | DAP adapter registration, launch arguments, starting `dlv dap` and the socket connection |
| `GoRunConfiguration.kt`, `GoRunConfigurationEditor.kt` | The Go run configuration, its settings page and plain Run |
| `GoLaunchSpec.kt` | `go run`/`go test` command lines and Delve `launch` arguments |
| `GoBreakpoints.kt` | Go line breakpoint type |
| `GoPackageActions.kt` | Editor right-click Run / Debug actions |
| `GoTools.kt` | Finds `go` and `dlv` |

## License

[MIT](LICENSE)
