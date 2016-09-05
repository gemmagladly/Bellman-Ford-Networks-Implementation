JFLAGS = -g
JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
		bfClientTester.java \
		bfClient.java \
		Host.java \
		RouteUpdater.java
default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class


