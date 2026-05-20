apiVersion: apps/v1
kind: Deployment
metadata:
  name: copa-backend
  namespace: copa-ticketing
  labels:
    app.kubernetes.io/name: copa-backend
    app.kubernetes.io/part-of: oracle-ddd-demo
    app.kubernetes.io/component: backend
spec:
  replicas: 2
  selector:
    matchLabels:
      app.kubernetes.io/name: copa-backend
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  template:
    metadata:
      labels:
        app.kubernetes.io/name: copa-backend
        app.kubernetes.io/part-of: oracle-ddd-demo
        app.kubernetes.io/component: backend
    spec:
      imagePullSecrets:
        - name: ocir-secret
      containers:
        - name: backend
          image: "${BACKEND_IMAGE}"
          imagePullPolicy: Always
          ports:
            - name: http
              containerPort: 8080
          envFrom:
            - secretRef:
                name: copa-app-secrets
          readinessProbe:
            httpGet:
              path: /health
              port: http
            initialDelaySeconds: 25
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 6
          livenessProbe:
            httpGet:
              path: /health
              port: http
            initialDelaySeconds: 60
            periodSeconds: 20
            timeoutSeconds: 5
          resources:
            requests:
              cpu: 250m
              memory: 512Mi
            limits:
              cpu: "1"
              memory: 1Gi
