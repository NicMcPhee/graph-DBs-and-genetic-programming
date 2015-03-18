#!/bin/bash

CSVFILE="../data/$1"

# If a parameter is not given, exit

if [ -z $CSVFILE ]; then
        echo "Please set csv file to split..."
        exit 0
fi

GEN=-1
COUNTER=0
FILENAME="data0.csv"
DIR=${CSVFILE::-4}
HEADER="not set yet"

# If a dir exist with existing name prompt for overwrite or new name

if [ -d "$DIR" ]; then
	echo "There is already a directory called $DIR. Type y to overwrite..."
	read ANS
	if [ $ANS == "y" ] || [ $ANS == "Y" ]; then
		rm -r "$DIR"
	else 
		echo "Please type another directory name to split csv files into..."
		read NEWDIR
		DIR=$NEWDIR
	fi
fi

mkdir $DIR

{
for ((i=1;i--;)) ;do	
	read line
	HEADER=${line//-/_}
done

while read line
do
	
        IFS=',' read -a array <<< "$line"
	array[3]=${array[3]//\"/}
	array[3]="${array[3]:1:${#array[3]}-2}"
	
	line=$( IFS=, ; echo "${array[*]}" )
	

#	echo "line is  $line"
	MOD=$(($COUNTER % 1000))
        if [ $MOD == "0" ] ; then
		GEN=$(($GEN+1))
                FILENAME="data$GEN.csv"
                echo "Writing to $DIR/$FILENAME"
                echo $HEADER >> $DIR/$FILENAME

        fi

	echo $line >> $DIR/$FILENAME
	COUNTER=$(($COUNTER + 1))
done 
} < $CSVFILE
echo "Done!"
exit 0
