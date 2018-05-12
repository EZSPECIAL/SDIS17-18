#!/bin/bash
java -cp ../src TestApp Peer1 BACKUP ../testFiles/feup.jpg 2
java -cp ../src TestApp Peer2 RESTORE ../testFiles/pixel.png
java -cp ../src TestApp Peer1 RESTORE ../testFiles/pixel.png
java -cp ../src TestApp Peer2 DELETE ../testFiles/report.pdf
read -n1 -r -p "Press any key to continue..."