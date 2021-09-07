# HJ_solver
This is our repository for building accelerators used in solving Hamiltonian-Jacobi partial differential equation (PDE) on an extended 4D Dubins Car dynamic system. In here, you will find:

1. Sources code in Chisel/Scala, documentation of the components in the accelerator architecture, how to compile, and running simulation to verify the hardware correctness

2. Instructions on how to deploy our accelerator on an AWS F1 instance, and how to write a C++ driver and a Pybind wrapper around it that does end-to-end computation on FPGA.

3. If you just want to use our pre-built accelerator on AWS FPGA. Please refer to the following page for more instructions.


## Getting Started on a Local Ubuntu Machine

* **[sbt:](https://www.scala-sbt.org/)** which is the preferred Scala build system and what Chisel uses.

* **[Verilator:](https://www.veripool.org/wiki/verilator)**, which compiles Verilog down to C++ for simulation. The included unit testing infrastructure uses this.


## (Ubuntu-like) Linux

Install Java

```
sudo apt-get install default-jdk
```

Install sbt, which isn't available by default in the system package manager:

```
echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 642AC823
sudo apt-get update
sudo apt-get install sbt
```

## Install Verilator.

We currently recommend Verilator version 4.016.

Follow these instructions to compile it from source.

1. Install prerequisites (if not installed already):

    ```bash
    sudo apt-get install git make autoconf g++ flex bison
    ```

2. Clone the Verilator repository:

    ```bash
    git clone http://git.veripool.org/git/verilator
    ```

3. In the Verilator repository directory, check out a known good version:

    ```bash
    git pull
    git checkout verilator_4_016
    ```

4. In the Verilator repository directory, build and install:

    ```bash
    unset VERILATOR_ROOT # For bash, unsetenv for csh
    autoconf # Create ./configure script
    ./configure
    make
    sudo make install
    ```
**Please remember that verialtor should be installed in the default system path, otherwise, chisel-iotesters won't find Verilator and the simulation can not be executed**

## HJ_solver's dependencies

Our current accelerator is using fixed point for computation. However, some inherited packages from the Dandelion project depends on _Berkeley Hardware Floating-Point Units_ for floating nodes. Therefore, before building HJ_solver you need to clone hardfloat project, build it and then publish it locally on your system. Hardfloat repository has all the necessary information about how to build the project, here we only briefly mention how to build it and then publish it.

```
git clone https://github.com/ucb-bar/berkeley-hardfloat.git
cd berkeley-hardfloat
sbt "publishLocal"
```

## Compiling HJ PDE solver accelerator
Run

```
make TOP=HJsolverSimAccel
```

# What's in the HJ PDE  repository?


