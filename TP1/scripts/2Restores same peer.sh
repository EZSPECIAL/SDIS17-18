#!/bin/bash
java -cp ../src TestApp Peer2 RESTORE ../testFiles/report.pdf
java -cp ../src TestApp Peer2 RESTORE ../testFiles/feup.jpg
read -n1 -r -p "Press any key to continue..."