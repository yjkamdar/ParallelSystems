#include <time.h>
#include <sys/time.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include "mpi.h"

/*Calculate the Average round trip time for all iterations of a node pair*/
double getIterAvg(double rtt[], int rttLen);

/*Calculate the Standard deviation of round trip times for all iterations of a node pair*/
double getIterStdDev(double rttAvg, double rtt[], int rttLen);

int main(int argc, char *argv[]){

        /*Process info and counters*/
        int nprc,rank,len;
        
        /*This is the root process*/
        int root =0;

        /*Initiate the sample size with 32. This keeps on increasing till the size is 2MB(2097152)*/
        long size=32;
        
        /*Array of all the round trip times for each iteration of any sample size*/
        double rtt[9];

        /*Time variables for performance*/
        struct timeval start, end;

        /*char array of size 2mb for the message passing*/
        char msg[2097152];

        /*Variable for number of iterations and the sampleSize counter. There will be 17 samples from 32 bytes to 2mb*/
        int iter=0,sampleCnt=1;

        /*Initializing MPI*/
        MPI_Init(&argc,&argv);

        /*Get the number of processes */
        MPI_Comm_size(MPI_COMM_WORLD, &nprc);

        /*Get the current process rank*/
        MPI_Comm_rank(MPI_COMM_WORLD, &rank);
        
        /*Get status of the communication*/
        int status;

        /*Get status of the message received*/
        MPI_Status mpi_status;

        /*get and print hostname*/
        char  hostname[MPI_MAX_PROCESSOR_NAME];
        MPI_Get_processor_name(hostname, &len);
        printf ("Initializing task %d on %s!\n", rank, hostname);

        if (rank == root)
                printf("MASTER: Number of MPI tasks is: %d\n", nprc);

        /*Run the while loop till sample count turns 17, since there are 17 sample sizes for each pair*/
        while(sampleCnt<18){
                if(rank%2==0){
                        /*Message send/receive logic for the first node of any pair*/

                        /*Initiate timer*/
                        gettimeofday(&start, NULL);

                        /*Send the message to the 2nd node and block till the receiver or the receiver buffer gets the message*/
                        status = MPI_Send(&msg,size,MPI_CHAR,rank+1,rank,MPI_COMM_WORLD);
                        if (status != MPI_SUCCESS){
                                printf("Error in sending data\n");
                                exit(1);        
                        }

                        /*Receive message from the 2nd node, thus completing the round trip*/
                        status = MPI_Recv(&msg, size, MPI_CHAR, rank+1, rank, MPI_COMM_WORLD, &mpi_status);
                        if (status != MPI_SUCCESS){
                                printf("Error in sending data\n");
                                exit(1);        
                        }

                        /*Stop timer*/
                        gettimeofday(&end, NULL);
                        
                        if(iter>0){
                                /*Store Round trip time for the iteration in an array for all iterations except the first.
                                We ignore the first iteration as it will also include the overhead of the initial handshake betwen the nodes*/
                                rtt[iter-1]=((end.tv_sec * 1000000 + end.tv_usec)  
                                        - (start.tv_sec * 1000000 + start.tv_usec))/1000000.0;
                        }
                }else{
                        /*Receive the message from the prmiary node of the pair*/
                        status = MPI_Recv(&msg, size, MPI_CHAR, rank-1, rank-1, MPI_COMM_WORLD, &mpi_status);
                        if (status != MPI_SUCCESS){
                                printf("Error in sending data\n");
                                exit(1);        
                        }

                        /*Send the message back to the primary node of the pair to complete the round trip*/
                        status = MPI_Send(&msg,size,MPI_CHAR,rank-1,rank-1,MPI_COMM_WORLD);
                        if (status != MPI_SUCCESS){
                                printf("Error in sending data\n");
                                exit(1);        
                        }
                }
                iter++;
                if(iter==11){
                        /*Calculate average round trip time, standard deviation after the last 
                        iteration of each size sample for each pair.*/

                        /*Array to store gathered info*/
                        double *rttAvgArr,*rttStdDevArr;
                        
                        double rttAvg,rttStdDev;
                        if (rank == root){
                                /*Allocate memory to arrays for data storing from each node*/
                                rttAvgArr = (double *)malloc(sizeof(double)* (nprc));
                                rttStdDevArr = (double *)malloc(sizeof(double)* (nprc));
                        }
                        
                        rttAvg=getIterAvg(rtt, 9);
                        rttStdDev=getIterStdDev(rttAvg,rtt, 9);

                        /*Gather the average of the specified sample size from each pair to the root node*/
                        status=MPI_Gather(&rttAvg, 1, MPI_DOUBLE, rttAvgArr, 1, MPI_DOUBLE, root, MPI_COMM_WORLD);
                        if (status != MPI_SUCCESS){
                                printf("Error in sending data\n");
                                exit(1);        
                        }

                        /*Gather the standard deviation of the specified sample size from each pair to the root node*/
                        status=MPI_Gather(&rttStdDev, 1, MPI_DOUBLE, rttStdDevArr, 1, MPI_DOUBLE, root, MPI_COMM_WORLD);
                        if (status != MPI_SUCCESS){
                                printf("Error in sending data\n");
                                exit(1);        
                        }

                        /*Print gathered results on the root node*/
                        if(rank == root){  
                                printf("%lu ",size);
                                for (int i = 0; i < nprc; ++i){
                                        if(i%2==0){
                                                printf("%lf %lf ",rttAvgArr[i],rttStdDevArr[i]);
                                        }
                                }
                                printf("\n");
                        }

                        /*Reset the iteration count to 0 for the next sample. And double the size for the next sample*/
                        iter=0;
                        sampleCnt++;
                        size=size*2;
                }
        }

        /*Exit gracefully*/
        MPI_Finalize();
        return 0;
}

double getIterAvg(double rtt[], int rttLen){
        int len=rttLen;
        double sum=0;
        for (int i = 0; i < len; ++i){
                sum+=rtt[i];
        }
        return sum/len;
}

double getIterStdDev(double rttAvg,double rtt[], int rttLen){
        int len=rttLen;
        double sum=0;
        for (int i = 0; i < len; ++i){
                sum+=pow((rtt[i]-rttAvg),2);
        }
        return sqrt(sum/len);
}