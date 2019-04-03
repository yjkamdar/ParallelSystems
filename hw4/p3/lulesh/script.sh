cpupower frequency-info
sudo cpupower frequency-set -f 1.5GHz
mpirun -np 8 -bind-to core ./lulesh 70 > cycles_1.5GHz.txt

cpupower frequency-info
sudo cpupower frequency-set -f 1.2GHz
mpirun -np 8 -bind-to core ./lulesh 70 > cycles_1.2GHz.txt

cpupower frequency-info
sudo cpupower frequency-set -f 1000MHz
mpirun -np 8 -bind-to core ./lulesh 70 > cycles_1000MHz.txt

cpupower frequency-info
sudo cpupower frequency-set -f 800MHz
mpirun -np 8 -bind-to core ./lulesh 70 > cycles_800MHz.txt

sudo cpupower frequency-set -g ondemand