#!/bin/sh

set -eu

usage() {
    echo "usage: $0 NEW_PROJECT_DIRECTORY [PACKAGE_NAME] [CHATMAP_DIRECTORY]" >&2
    echo "example: $0 /d/Dev/projects/hello org.ray.hello" >&2
    exit 2
}

[ "$#" -ge 1 ] && [ "$#" -le 3 ] || usage

project_directory=$1
project_name=$(basename "$project_directory")
default_package=$(printf '%s' "$project_name" | tr '[:upper:]-' '[:lower:]_')
package_name=${2:-$default_package}
chatmap_directory=${3:-$(dirname "$project_directory")/chatmap}

case "$project_name" in
    ""|*[!A-Za-z0-9._-]*)
        echo "error: project name must contain only letters, digits, dots, underscores, or hyphens" >&2
        exit 1
        ;;
esac

if ! printf '%s\n' "$package_name" | grep -Eq '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$'; then
    echo "error: invalid Java package name: $package_name" >&2
    exit 1
fi

if [ -e "$project_directory" ]; then
    echo "error: destination already exists: $project_directory" >&2
    exit 1
fi

for wrapper_file in \
    gradlew \
    gradlew.bat \
    gradle/wrapper/gradle-wrapper.jar \
    gradle/wrapper/gradle-wrapper.properties
do
    if [ ! -f "$chatmap_directory/$wrapper_file" ]; then
        echo "error: ChatMap wrapper file not found: $chatmap_directory/$wrapper_file" >&2
        exit 1
    fi
done

package_directory=$(printf '%s' "$package_name" | tr '.' '/')

mkdir -p \
    "$project_directory/gradle/wrapper" \
    "$project_directory/src/$package_directory" \
    "$project_directory/tst/$package_directory"

cp "$chatmap_directory/gradlew" "$project_directory/gradlew"
cp "$chatmap_directory/gradlew.bat" "$project_directory/gradlew.bat"
cp "$chatmap_directory/gradle/wrapper/gradle-wrapper.jar" \
    "$project_directory/gradle/wrapper/gradle-wrapper.jar"
cp "$chatmap_directory/gradle/wrapper/gradle-wrapper.properties" \
    "$project_directory/gradle/wrapper/gradle-wrapper.properties"
chmod +x "$project_directory/gradlew"

cat > "$project_directory/settings.gradle.kts" <<EOF
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "$project_name"
EOF

cat > "$project_directory/build.gradle.kts" <<'EOF'
plugins {
    java
    eclipse
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources {
            setSrcDirs(listOf("src"))
            exclude("**/*.java")
        }
    }
    test {
        java.setSrcDirs(listOf("tst"))
        resources {
            setSrcDirs(listOf("tst"))
            exclude("**/*.java")
        }
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
EOF

cat > "$project_directory/.gitignore" <<'EOF'
.gradle/
/build/
/bin/

.classpath
.project
.settings/

*.class
*.log
*.tmp
EOF

cat > "$project_directory/src/$package_directory/Example.java" <<EOF
package $package_name;

class Example {
    public int add(int left, int right) {
        return left + right;
    }
}
EOF

cat > "$project_directory/tst/$package_directory/ExampleTest.java" <<EOF
package $package_name;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExampleTest {
    @Test
    void addsTwoNumbers() {
        assertEquals(5, new Example().add(2, 3));
    }
}
EOF

cat > "$project_directory/README.md" <<EOF
# $project_name

Build and test:

\`\`\`sh
./gradlew test
\`\`\`

Regenerate Eclipse metadata:

\`\`\`sh
./gradlew eclipse
\`\`\`
EOF

(
    cd "$project_directory"
    ./gradlew eclipse test
    ./gradlew clean
)

echo
echo "Created and verified: $project_directory"
echo "Eclipse: File -> Import -> Existing Projects into Workspace"
echo "Leave 'Copy projects into workspace' unchecked."
