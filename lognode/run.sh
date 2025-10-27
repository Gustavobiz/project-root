#!/usr/bin/env bash
cd "$(dirname "$0")"
java -cp target NodeMain --role=follower --transport=http --port=5000 --gateway=http://localhost:8080 --nodeId=nodeA
