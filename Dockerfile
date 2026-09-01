# The image Render runs the beta server from (M15.2, D032).
#
# Two stages: a JDK builds the server distribution from source, and a JRE runs it. The
# runtime stage carries no build tooling, no Gradle cache, and no source — and no secret.
# DATABASE_URL and SUPABASE_URL are supplied by the host's environment at run time and are
# never baked in, so this image is not a thing that has to be kept out of a registry.
#
# Build and run it locally exactly as Render does; see docs/DEVELOPMENT.md.

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
FROM eclipse-temurin:24-jdk AS build

WORKDIR /workspace

# One copy rather than a dependency layer and then a source layer: Render builds with no
# layer cache, so splitting them would buy nothing where it matters. .dockerignore keeps
# the context to what the build actually reads.
COPY . .

# -PserverOnly leaves :android-app out of the build. It needs an Android SDK, which a JDK
# build image has no reason to carry, and configuring it would fail the deploy before a
# line of server code compiled. See settings.gradle.kts.
#
# installDist produces build/install/server: a start script and the runtime classpath,
# including the SQL migrations that server/build.gradle.kts copies onto it.
RUN chmod +x gradlew \
    && ./gradlew --no-daemon -PserverOnly=true :server:installDist

# ---------------------------------------------------------------------------
# Run
# ---------------------------------------------------------------------------
FROM eclipse-temurin:24-jre-alpine AS runtime

# Not root. Nothing here writes to the filesystem, and a container that cannot is a
# smaller problem when something else goes wrong.
RUN addgroup --system chessgame \
    && adduser --system --ingroup chessgame --home /app --disabled-password chessgame

WORKDIR /app

COPY --from=build --chown=chessgame:chessgame /workspace/server/build/install/server ./

USER chessgame

# The JVM defaults to a quarter of the container's memory, which is thin in the 512 MB a
# free instance gets once Netty, Hikari, and Flyway are in it. Serial GC because that
# instance is a fraction of one CPU, where a parallel collector's threads only contend.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"

# The local default. A host that wants another port sets PORT and the server binds that
# instead (see Application.kt), which is how Render routes to this container.
EXPOSE 8080

# The Gradle start script execs the JVM, so the JVM is PID 1 and receives the SIGTERM a
# host sends when it replaces an instance.
ENTRYPOINT ["/app/bin/server"]
