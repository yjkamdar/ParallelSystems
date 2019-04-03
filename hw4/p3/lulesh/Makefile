#Flags for MPI + OPENMP
CXXFLAGS = -I. -I${PAPI_INC} 
LDFLAGS_OPENMP = -g -O3 -fopenmp -L${PAPI_LIB} -lpapi
LDFLAGS = -g -O3 

MPI_OPENMP: 
	mpic++ luleshMPI_OMP.cc $(CXXFLAGS) $(LDFLAGS_OPENMP) -o lulesh

MPI_ONLY:
	mpic++ luleshMPI_OMP.cc $(LDFLAGS) -o lulesh

clean:
	/bin/rm -f lulesh
	/bin/rm -rf *.dSYM

