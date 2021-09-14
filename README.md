# pmi
Introduction of Performance Monitoring Infrastructure

## Import eclipse project

```
git clone https://github.com/pdprof/pmi.git
```

- Start eclipse
- Import General > Existing Projects into Workspace
- Select cloned git directory and db.connections projects and Import
- right click on db.connections project.
- select Maven > Update project 
- Check db.connections project and push OK button.

## Test your project on your local liberty

Build project on your eclipse environment.
If you are not fimiliar with developing web project with eclipse, please refer other document to do so.

Alternatively, you can run each application with `mvn liberty:dev` in Eclipse project directory.
Note that this operation creates copy of liberty server on working directory (${basedir}/target) of each project.
Please clean up them with `mvn clean` when they become unnecessary.

## Use docker and openshhit image to do mustgather hands on

Please take a look at ***-docker/mustgather-*** directory
