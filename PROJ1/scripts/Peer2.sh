#!/bin/bash
mytitle="Peer2"
echo -e '\033]2;'$mytitle'\007'
java -cp ../src StartPeer 1.0 2 Peer2 225.0.0.1:1024 225.0.0.2:1025 225.0.0.3:1026 VERBOSE CONSOLE