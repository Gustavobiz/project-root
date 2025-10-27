set -e
cd "$(dirname "$0")"
mkdir -p target
javac -d target src/*.java
echo "✅ LogNode compilado em lognode/target"
