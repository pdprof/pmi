# pmi
Introduction of Performance Monitoring Infrastructure

## Import Eclipse projects

```
git clone https://github.com/pdprof/pmi.git
```

- Start Eclipse
- Import General > Existing Projects into Workspace
- Select cloned git directory and enable to search nested projects
- Select following Eclipse projects
  - [workloads](workloads/)
  - [collector-rest](collector-rest/)
- Select pom.xml of each project and Run as > mvn install
- 

## Test your project with Liberty locally

Deploy each projects as WAR to pre-defined Liberty server and start server with Eclipse.

If you are not fimiliar with Eclipse, run Liberty as maven goal with [Liberty Maven Plugin](https://github.com/OpenLiberty/ci.maven).

```
mvn liberty:dev
```

in Eclipse project directory.

Note that this operation creates copy of Liberty server on working directory (${basedir}/target) of each projects.
Please clean up them with `mvn clean` when they become unnecessary.

## Run docker images with local Docker Engine or OpenShift

- Use `docker build` and `docker run` to run images locally.
- Run `setup-openshift.sh` in each projects to run images on OpenShift.  
  `oc login` to api-server of OpenShift is required before running this script.
