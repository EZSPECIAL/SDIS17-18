#!/bin/bash
java -cp ../src TestApp Peer2 RESTORE ../testFiles/pixel.png
java -cp ../src TestApp Peer1 RESTORE ../testFiles/pixel.png
read -n1 -r -p "Press any key to continue..."