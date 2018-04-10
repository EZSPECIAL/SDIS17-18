#!/bin/bash
java -cp ../src TestApp Peer2 DELETE ../testFiles/pixel.png
java -cp ../src TestApp Peer1 DELETE ../testFiles/pixel.png
read -n1 -r -p "Press any key to continue..."