#!/bin/bash
mytitle="Peer4Enh"
echo -e '\033]2;'$mytitle'\007'
winpty java -cp ../src StartPeer 1.1 4 Peer4 225.0.0.1:1024 225.0.0.2:1025 225.0.0.3:1026 DEBUG CONSOLE