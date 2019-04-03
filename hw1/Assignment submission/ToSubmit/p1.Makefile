#Single Author info:
#
#yjkamdar Yash J Kamdar
#
#
#Generate executable file
p1: p1.c
        mpicc -lm -O3 -o p1 p1.c

#Clean previous makes
clean:
        rm p1