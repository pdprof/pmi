#!/bin/bash

# prereq : oc login is required to execute this shell

oc registry login --skip-check
docker login -u $(oc whoami) -p $(oc whoami -t) $(oc registry info)

APP_NAME=$(basename $(cd $(dirname $0); pwd))
BUILD_DATE=`date +%Y%m%d%H%M%S`

docker build -t $(oc registry info)/$(oc project -q)/${APP_NAME}:${BUILD_DATE} .
docker push $(oc registry info)/$(oc project -q)/${APP_NAME}:${BUILD_DATE}

oc apply -f kubernetes.yaml
oc set image deployments/${APP_NAME} ${APP_NAME}=image-registry.openshift-image-registry.svc:5000/$(oc project -q)/${APP_NAME}:${BUILD_DATE}
