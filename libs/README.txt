Local compile/test dependencies (Java 21, Minecraft 1.21.1 / NeoForge)

Third-party JARs are not redistributed. Supply compatible original artifacts under
these filenames, as declared by build.gradle:

create.jar              Create (current integration baseline: 6.0.10)
cbc.jar                 Create: Big Cannons 5.11.7
sable.jar               Sable integration API
sable-companion.jar     Sable companion / Sable Hostile integration API
iris.jar                Iris integration API
healthbar-api.jar       Sable Healthbar integration API
wolfmisc.jar            Wolf's Misc integration API
afn.jar                 Create: Advanced Frequency Network integration API
ldlib2.jar              LDLib2 (2.2.17 or compatible)
taffy.jar, yoga.jar     embedded LDLib2 dependencies
cbc-deps/*.jar          CBC/Create dependencies, including required embedded APIs

Runtime-optional integrations may still be needed to compile source and run
binary contract tests. Arbitrary versions are not guaranteed API-compatible.
prepare-libs.ps1 is a legacy helper for a subset of these files, not an automatic
installer for the entire current dependency set. Obtain binaries from their
original publishers; do not commit them to the repository.
