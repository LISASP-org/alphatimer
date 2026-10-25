# Shared by the start scripts (sourced, expects $self = the start script with links resolved): finds Java 25 or newer
# and starts the given main class with its own jar, the shared dependencies and the JavaFX libraries of this system.
# Only that one program jar goes on the class path, since each of them brings its own logback.xml.
ALPHATIMING_HOME=$(cd "$(dirname "$self")" && pwd -P)

fail() {
    echo "AlphaTiming: $1" >&2
    exit 127
}

launch() {
    jar=$1
    main=$2
    shift 2
    if [ -n "$JAVA_HOME" ]; then
        java=$JAVA_HOME/bin/java
    else
        java=java
    fi
    command -v "$java" >/dev/null 2>&1 || fail "Java 25 or newer is required, but no java was found (set JAVA_HOME or add java to PATH)."
    version=$("$java" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')
    case $version in
        '' | 1.*) major=0 ;;
        *) major=${version%%.*} ;;
    esac
    [ "$major" -ge 25 ] || fail "Java 25 or newer is required, but $java is Java ${version:-of unknown version}."
    case $(uname -s) in
        Linux) platform=linux ;;
        Darwin) platform=mac ;;
        *) fail "$(uname -s) is not supported; use the JAuswertung installer on Windows." ;;
    esac
    exec "$java" --enable-native-access=ALL-UNNAMED \
        -cp "$ALPHATIMING_HOME/lib/$jar:$ALPHATIMING_HOME/lib/dependencies/*:$ALPHATIMING_HOME/lib/javafx/$platform/*" "$main" "$@"
}
