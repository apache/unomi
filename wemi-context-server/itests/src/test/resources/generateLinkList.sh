#!/bin/zsh
rm linklist.txt
count=1;
while read url
do
    curl $url 2>&1 | grep -o -E 'href="([^"#]+)"' | cut -d'"' -f2 | grep html > links
    count=$(($count + 1))

	while read link
	do
		linenumber=`grep -n "$link" urllist.txt | cut -d : -f1 | head -1`
		print -n "$linenumber " >> linklist.txt
	done < links

    rm links
	echo >> linklist.txt
done < urllist.txt
