JAVA_CLASS_FILES  := $(subst .java,.class,$(shell ls *.java))
free_mem := $(shell echo `cat /proc/meminfo  | grep MemFree | cut -f2 -d: | xargs | cut -f1 -d' '`/1024/1024 | bc)

all:JLIBS $(JAVA_CLASS_FILES)

WG2Bin: JLIBS WG2Bin.class	
	@echo "args = " $(args)
	@if [ ! -e  `echo $(args)| cut -f1 -d' '`.offsets ]; then\
		echo -e "\n\033[1;31mCreatinig .offsets file\033[0;37m";\
		java -Xmx$(free_mem)G -cp jlibs/*: it.unimi.dsi.big.webgraph.BVGraph `echo $(args)| cut -f1 -d' '` -O;\
		echo ;\
	fi
	java -Xmx$(free_mem)G -ea -cp jlibs/*: WG2Bin $(args)	

WG2BinAL:JLIBS WG2BinAL.class	
	java -Xmx$(free_mem)G -ea -cp jlibs/*: WG2BinAL $(args)	

JLIBS: FORCE 
	@if [ `javac  -version 2>&1 | cut -f2 -d' ' | cut -f1 -d.` -le 14 ]; then\
		javac  -version 2>&1;\
		echo -e "\033[0;33mError:\033[0;37m Version 15 or newer is required for javac.\n\n";\
		exit -1;\
	fi
	@if [ `java  -version 2>&1 | head -n1|cut -f2 -d\"|cut -f1 -d.` -le 14 ]; then\
		java  -version 2>&1;\
		echo -e "\033[0;33mError:\033[0;37m Version 15 or newer is required for java.\n\n";\
		exit -1;\
	fi
	@if [ ! -d jlibs ]; then\
		wget "https://hpgp.net/download/jlibs.zip";\
		unzip jlibs.zip $(PARAGRAPHER_LIB_FOLDER); \
		rm jlibs.zip;\
		echo "Java libararies downloaded.";\
	fi

%.class: %.java Makefile
	@echo -e "\n\033[1;34mCompiling $<\033[0;37m"
	javac -cp jlibs/*: $<

test:
	@if [ ! -f data/cnr-2000.graph ]; then \
		mkdir -p data; \
		echo -e "--------------------\n\033[1;34mDownloading CNR-2000\033[0;37m"; \
		wget -P data "http://data.law.di.unimi.it/webdata/cnr-2000/cnr-2000.graph"; \
		wget -P data "http://data.law.di.unimi.it/webdata/cnr-2000/cnr-2000.properties"; \
		echo -e "--------------------\n";\
	fi
	make WG2Bin args="data/cnr-2000 data"

clean: 
	rm -f *.class jlibs.zip
	touch *.java 

touch:
	touch *.java

FORCE: ;
