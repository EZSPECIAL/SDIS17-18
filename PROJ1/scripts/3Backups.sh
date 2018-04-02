#!/bin/bash
java -cp ../src TestApp Peer1 BACKUP ../testFiles/feup.jpg 2
java -cp ../src TestApp Peer2 BACKUP ../testFiles/pixel.png 1
java -cp ../src TestApp Peer2 BACKUP ../testFiles/report.pdf 2
read -n1 -r -p "Press any key to continue..."