#!/bin/bash

NAME="broadcast-config"

kubectl delete configmap $NAME
kubectl create configmap $NAME --from-file=../conf