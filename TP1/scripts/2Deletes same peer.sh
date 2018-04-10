#!/bin/bash
java -cp ../src TestApp Peer2 DELETE ../testFiles/feup.jpg
java -cp ../src TestApp Peer2 DELETE ../testFiles/pixel.png
read -n1 -r -p "Press any key to continue..."