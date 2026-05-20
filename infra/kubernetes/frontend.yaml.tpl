apiVersion: apps/v1
kind: Deployment
metadata:
  name: copa-frontend
  namespace: copa-ticketing
  labels:
    app.kubernetes.io/name: copa-frontend
    app.kubernetes.io/part-of: oracle-ddd-demo
    app.kubernetes.io/component: frontend
spec:
  replicas: 2
  selector:
    matchLabels:
      app.kubernetes.io/name: copa-frontend
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  template:
    metadata:
      labels:
        app.kubernetes.io/name: copa-frontend
        app.kubernetes.io/part-of: oracle-ddd-demo
        app.kubernetes.io/component: frontend
    spec:
      imagePullSecrets:
        - name: ocir-secret
      containers:
        - name: frontend
          image: "${FRONTEND_IMAGE}"
          imagePullPolicy: Always
          ports:
            - name: http
              containerPort: 8080
          envFrom:
            - secretRef:
                name: copa-app-secrets
          readinessProbe:
            tcpSocket:
              port: http
            initialDelaySeconds: 45
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 6
          livenessProbe:
            tcpSocket:
              port: http
            initialDelaySeconds: 90
            periodSeconds: 20
            timeoutSeconds: 5
          resources:
            requests:
              cpu: 500m
              memory: 768Mi
            limits:
              cpu: "2"
              memory: 2Gi
