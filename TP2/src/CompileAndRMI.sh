#!/bin/bash
mytitle="RMI Registry"
echo -e '\033]2;'$mytitle'\007'
javac *.java
rmiregistry
read -n1 -r -p "Press any key to continue..."