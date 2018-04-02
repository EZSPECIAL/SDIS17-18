#!/bin/bash
java -cp ../src TestApp Peer1 BACKUP ../testFiles/pixel.png 2
java -cp ../src TestApp Peer2 BACKUP ../testFiles/pixel.png 1
read -n1 -r -p "Press any key to continue..."