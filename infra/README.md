# OCI OKE demo - oracle-ddd

Projeto Terraform + Kubernetes para provisionar uma infraestrutura simples na OCI e publicar a aplicacao `oracle-ddd` no OKE.

Este projeto foi montado para demonstração. A rede fica propositalmente pública e permissiva. Não use este desenho como referência de produção sem revisar segurança, rede, secrets, banco de dados, observabilidade, backup e políticas IAM.

## O que este projeto cria

Via Terraform:

- VCN pública com Internet Gateway e rota para internet.
- 3 subnets públicas separadas:
  - subnet do control plane / Kubernetes API endpoint;
  - subnet dos workers;
  - subnet dos load balancers.
- Security rules abertas para demo.
- Cluster OKE.
- Node pool gerenciado.
- Repositorios no OCIR para as imagens:
  - backend;
  - frontend.
- MySQL HeatWave
  - Network Load Balancer para expor um IP público para o MySQL

Via Kubernetes:

- Namespace da aplicação.
- Secret com variáveis da aplicação.
- Deployment do backend.
- Deployment do frontend.
- Services internos.
- Service pÚblico do tipo `LoadBalancer` para o frontend.

## Estrutura atual

Este README assume que você esta trabalhando dentro da pasta `infra` do repositório.
s
```text
infra/
|-- .env.example
|-- .env                         # criado manualmente a partir do .env.example; não versionar
|-- generated/                   # gerado pelos scripts; não versionar
|   |-- kubeconfig
|   `-- k8s/
|       |-- backend.yaml
|       |-- frontend.yaml
|       `-- secrets.yaml
|-- kubernetes/
|   |-- backend.yaml.tpl
|   |-- frontend.yaml.tpl
|   |-- namespace.yaml
|   |-- secrets.yaml.tpl
|   |-- service-loadbalancer.yaml
|   `-- services.yaml
|-- scripts/
|   |-- lib.sh
|   |-- 01_apply_terraform.sh
|   |-- 02_build_and_push_images.sh
|   |-- 03_import_sql.sh
|   |-- 04_deploy_k8s.sh
|   |-- 99_destroy.sh
|   `-- images.env               # gerado pelo script 02; não versionar
|   `-- db.env                   # gerado pelo script 03; não versionar
`-- terraform/
    |-- main.tf
    |-- variables.tf
    |-- outputs.tf
    `-- modules/
```

O fluxo correto é executar os scripts manualmente em sequência:

```bash
./scripts/01_apply_terraform.sh
./scripts/02_build_and_push_images.sh
./scripts/03_import_sql.sh
./scripts/04_deploy_k8s.sh
```

Para destruir o ambiente:

```bash
./scripts/99_destroy.sh
```

## Pré-requisitos locais

Instale e valide os comandos abaixo:

- Terraform.
- OCI CLI.
- kubectl.
- mysql.
- podman.
- git.
- envsubst, normalmente via pacote `gettext`.
- bash, preferencialmente Git Bash, WSL ou Linux/macOS.

Valide rapidamente:

```bash
terraform version
oci --version
kubectl version --client
podman version
git --version
envsubst --version
mysql --version
```

## 1. Criar API Key na OCI

O Terraform usa autenticação `APIKey`, então você precisa de uma chave privada local, uma chave pública cadastrada no usuario OCI e os dados de OCID/fingerprint no `.env`.

No Console da OCI:

1. Abra o menu de perfil no canto superior direito.
2. Entre em `User settings`.
3. Abra `Tokens and keys`.
4. Na seção `API keys`, clique em `Add API key`.
5. Escolha a opção`Generate API key pair` e clique nas duas chaves para download.
6. Clique em `add`:
7. Copie as informações que iremos trocar no .env

Depois preencha no `.env`:

```bash
export TF_VAR_user_ocid="<OCID_DO_USER>"
export TF_VAR_fingerprint="<FINGERPRINT_DA_API_KEY>"
export TF_VAR_private_key_path="<ALTERAR_PARA_CAMINHO_DA_CHAVE_PRIVADA>"
```

Tambem preencha, você pode pegar o OCID do compartment ou da tenancy (root) indo em Menu -> Identity &Security -> Identity -> Compartments. Você pode usar apenas o OCID da tenancy colocando o valor dela nos dois campos, caso tenha um compartment de preferência, cole ele no campo de compartment:

```bash
export TF_VAR_tenancy_ocid="<OCID_DA_TENANCY>"
export TF_VAR_compartment_ocid="<OCID_DO_COMPARTMENT>"
export TF_VAR_region="<REGIAO>"
```

Exemplo de região:

```bash
export TF_VAR_region="sa-saopaulo-1"
```

## 2. Criar o `.env`

Copie o exemplo:

```bash
cp .env.example .env
```

Edite o arquivo:

```bash
vi .env
```

Ou use seu editor preferido.

### Modelo esperado do `.env`

Use o `.env.example` como base. O arquivo final deve ter `export` em todas as variaveis usadas pelos scripts.

```bash
# Terraform
export TF_VAR_project_name="oracle-ddd-demo"
export TF_VAR_region="<ALTERAR_PARA_REGIAO>"
export TF_VAR_tenancy_ocid="<ALTERAR_PARA_OCID_DA_TENANCY>"
export TF_VAR_compartment_ocid="<ALTERAR_PARA_OCID_DO_COMPARTMENT>"

# Autenticacao OCI
export TF_VAR_auth="APIKey"
export TF_VAR_user_ocid="<ALTERAR_PARA_OCID_DO_USER>"
export TF_VAR_fingerprint="<ALTERAR_PARA_FINGERPRINT>"
export TF_VAR_private_key_path="<ALTERAR_PARA_CAMINHO_DA_CHAVE_PRIVADA>"

# OKE
export TF_VAR_node_count="2"
export TF_VAR_node_shape="VM.Standard.E4.Flex"
export TF_VAR_node_ocpus="2"
export TF_VAR_node_memory_in_gbs="16"
export TF_VAR_kubernetes_version=""
export FORCE_ROLLOUT_RESTART="true"
export ROLLOUT_TIMEOUT="600s"

# Imagem / OCIR
# Formato do username: <tenancy-namespace>/<oci-username>
# Para usuario federado, normalmente: <tenancy-namespace>/<identity-domain>/<username>
export OCIR_USERNAME="<ALTERAR_PARA_USERNAME>"
export OCIR_AUTH_TOKEN="<ALTERAR_PARA_AUTH_TOKEN>"
export IMAGE_TAG="demo"
export DOCKER_PLATFORM="linux/amd64"

# Aplicacao
export BACKEND_PORT="8080"
export DB_POOL_SIZE="20"
export ADMIN_USER="admin"
export ADMIN_PASS="changeme"
export CUSTOMER_USER="customer"
export CUSTOMER_PASS="changeme"
export OCI_GENAI_API_KEY="<ALTERAR_PARA_API_KEY_GENAI>"
export OCI_GENAI_MODEL_ID="meta.llama-3.3-70b-instruct"
export HEATWAVE_NL_SQL_MODEL_ID="cohere.command-r-plus-08-2024"
export K8S_NAMESPACE="copa-ticketing"

export FRONTEND_PORT="8081"
export BACKEND_URL="http://copa-backend.copa-ticketing.svc.cluster.local:8080"
export BACKEND_CUSTOMER_USER="customer"
export BACKEND_CUSTOMER_PASS="changeme"
export BACKEND_ADMIN_USER="admin"
export BACKEND_ADMIN_PASS="changeme"

# MySQL HeatWave
export TF_VAR_mysql_admin_username="admin"
export TF_VAR_mysql_admin_password="123Oracle123@"
export TF_VAR_mysql_shape_name="MySQL.2"
export TF_VAR_mysql_data_storage_size_in_gb="50"
export TF_VAR_mysql_auto_expand_storage="true"
export TF_VAR_mysql_max_storage_size_in_gbs="100"
export TF_VAR_mysql_port="3306"
export TF_VAR_mysql_port_x="33060"
export TF_VAR_mysql_database_name="copa_ticketing_demo"
export TF_VAR_mysql_enable_heatwave_cluster="true"
export TF_VAR_mysql_heatwave_shape_name="HeatWave.32GB"
export TF_VAR_mysql_heatwave_cluster_size="1"

# Import SQL
export SQL_FILE_PATH="../database/000_terraform_schema_views_user_only.sql"
export MYSQL_IMPORT_USER="${TF_VAR_mysql_admin_username}"
export MYSQL_IMPORT_PASSWORD="${TF_VAR_mysql_admin_password}"
```

## 3. Aplicar Terraform

Com o `.env` criado:

```bash
source .env
./scripts/01_apply_terraform.sh
```

Este script executa o Terraform e cria a infraestrutura OCI.

Ao final, confira os outputs:

```bash
terraform -chdir=terraform output
```

Os outputs mais importantes para os próximos passos são:

- endpoint do OCIR;
- URL dos repositórios backend e frontend;
- kubeconfig;
- dados do cluster OKE.
- dados do MySQL

## 4. Criar Auth Token para OCIR

O `OCIR_AUTH_TOKEN` não é a senha do Console OCI. Ele é um Auth Token do usuário OCI.

No Console da OCI:

1. Abra o menu de perfil no canto superior direito.
2. Entre em `User settings`.
3. Abra `Tokens and keys`.
4. Na seção `Auth tokens`, clique em `Generate token`.
5. Informe uma descrição, por exemplo `ocir-oracle-ddd-demo`.
6. Clique em `Generate token`.
7. Copie o token.

Depois coloque no `.env`:

```bash
export OCIR_AUTH_TOKEN="<AUTH_TOKEN_GERADO>"
```

O token só é exibido uma vez. Se perder, gere outro.

## 5. Preencher OCIR_USERNAME

É mais fácil preencher `OCIR_USERNAME` depois de rodar o Terraform, porque os repositórios OCIR já existem e você consegue visualizar o namespace no recurso do OCIR ou nos outputs.

O formato comum e:

```bash
export OCIR_USERNAME="<tenancy-namespace>/<oci-username>"
```

Exemplo:

```bash
export OCIR_USERNAME="grxz8dbxowkb/joao.silva@empresa.com"
```

Para usuários federados, o formato pode incluir o domínio de identidade:

```bash
export OCIR_USERNAME="<tenancy-namespace>/<identity-domain>/<username>"
```

Exemplo:

```bash
export OCIR_USERNAME="grxz8dbxowkb/OracleIdentityCloudService/joao.silva@empresa.com"
```

Como descobrir o namespace:

```bash
terraform -chdir=terraform output
```

Ou consulte no Console da OCI nos detalhes da tenancy ou no recurso do Container Registry criado pelo Terraform.

Depois de alterar `OCIR_USERNAME` e `OCIR_AUTH_TOKEN`, recarregue o ambiente:

```bash
source .env
```

## 6. Build e push das imagens

Execute:

```bash
./scripts/02_build_and_push_images.sh
```

Esse script deve:

1. ler os outputs do Terraform;
2. fazer login no OCIR usando `OCIR_USERNAME` e `OCIR_AUTH_TOKEN`;
3. buildar a imagem do backend;
4. publicar a imagem do backend no OCIR;
5. buildar a imagem do frontend;
6. publicar a imagem do frontend no OCIR;
7. gravar o arquivo `scripts/images.env`.

O arquivo gerado deve ter conteúdo parecido com:

```bash
export BACKEND_IMAGE="gru.ocir.io/<namespace>/oracle-ddd-demo/backend:demo"
export FRONTEND_IMAGE="gru.ocir.io/<namespace>/oracle-ddd-demo/frontend:demo"
export IMAGE_TAG="demo"
```

Esse arquivo é gerado automaticamente. Não edite manualmente e não versione no Git.

## 7. Configurar OCI Generative AI

A variável `OCI_GENAI_API_KEY` usa uma API key do serviço OCI Generative AI. Ela é diferente da API key IAM criada na seção 1 para o Terraform.

No Console da OCI:

1. Selecione a região do modelo que será usado. O backend atual chama o endpoint `us-chicago-1`, então crie a chave em `US Midwest (Chicago)` ou ajuste o endpoint no código para a região escolhida.
2. Abra o menu e vá em `Analytics & AI` -> `AI Services` -> `Generative AI`.
3. No menu de `Generative AI`, entre em `API keys`.
4. Clique em `Create API key`.
5. Informe nome, compartment e, se quiser, datas de expiração para `key-one` e `key-two`.
6. Clique em `Create` e copie o segredo de `key-one` ou `key-two`. Esse segredo, e não o OCID da chave, é o valor do `.env`.

Depois preencha:

```bash
export OCI_GENAI_API_KEY="<SEGREDO_KEY_ONE_OU_KEY_TWO>"
```

Também é necessário criar uma policy IAM permitindo o uso da API key pelo Generative AI:

```text
allow any-user to use generative-ai-family in tenancy where ALL { request.principal.type='generativeaiapikey' }
```

Para restringir mais, crie uma policy equivalente limitando por compartment, OCID da API key, modelo ou operação. Para a demo, a policy acima libera as chaves de Generative AI no tenancy inteiro.

### Modelos para `OCI_GENAI_MODEL_ID`

`OCI_GENAI_MODEL_ID` é enviado no campo `model` do endpoint `chat/completions` usado pelo backend. Use um modelo disponível na mesma região da `OCI_GENAI_API_KEY`.

Com o endpoint atual em `us-chicago-1`, use um destes modelos de chat suportados pelo fluxo de API key:

```text
# Meta Llama
meta.llama-4-maverick-17b-128e-instruct-fp8
meta.llama-4-scout-17b-16e-instruct
meta.llama-3.3-70b-instruct
meta.llama-3.3-70b-instruct-fp8-dynamic

# OpenAI
openai.gpt-oss-120b
openai.gpt-oss-20b

# xAI Grok
xai.grok-4.3
xai.grok-4.20-0309-reasoning
xai.grok-4.20-reasoning
xai.grok-4.20-0309-non-reasoning
xai.grok-4.20-non-reasoning
```
Ref: https://docs.oracle.com/en-us/iaas/Content/generative-ai/pretrained-models.htm

Não use modelos de embedding, rerank, voice ou NL_SQL nessa variável. O modelo de NL_SQL continua separado em `HEATWAVE_NL_SQL_MODEL_ID`.

Exemplo recomendado para a demo:

```bash
export OCI_GENAI_MODEL_ID="meta.llama-3.3-70b-instruct"
```

## 8. Provisionamento e importação do MySQL HeatWave

O MySQL HeatWave é criado pelo Terraform durante a execução do script `01_apply_terraform.sh`.

Após o Terraform finalizar, o projeto terá os outputs necessários para conexão com o banco, como:

```bash
mysql_public_ip
mysql_public_endpoint
mysql_public_jdbc_url
mysql_database_name
```

O banco é exposto publicamente para facilitar a demonstração. Essa abordagem é apenas para demo.

### Arquivo SQL

O script de importação espera encontrar o arquivo SQL no caminho configurado pela variável:

```bash
export SQL_FILE_PATH="../database/000_terraform_schema_views_user_only.sql"
```

Considerando que os scripts rodam a partir da pasta `infra`, a estrutura esperada é:

```text
oracle-ddd/
├── database/
│   └── 000_terraform_schema_views_user_only.sql
└── infra/
    ├── scripts/
    ├── terraform/
    └── kubernetes/
```

### Variáveis necessárias no `.env`

Antes de executar o import, confira estas variáveis no `.env`:

```bash
# MySQL HeatWave
export TF_VAR_mysql_admin_username="admin"
export TF_VAR_mysql_admin_password="<ALTERAR_PARA_SENHA_ADMIN_DO_MYSQL>"
export TF_VAR_mysql_shape_name="MySQL.2"
export TF_VAR_mysql_data_storage_size_in_gb="50"
export TF_VAR_mysql_auto_expand_storage="true"
export TF_VAR_mysql_max_storage_size_in_gbs="100"
export TF_VAR_mysql_port="3306"
export TF_VAR_mysql_port_x="33060"
export TF_VAR_mysql_database_name="copa_ticketing_demo"
export TF_VAR_mysql_enable_heatwave_cluster="true"
export TF_VAR_mysql_heatwave_shape_name="HeatWave.32GB"
export TF_VAR_mysql_heatwave_cluster_size="1"

# Import SQL
export SQL_FILE_PATH="../database/000_terraform_schema_views_user_only.sql"
export MYSQL_IMPORT_USER="${TF_VAR_mysql_admin_username}"
export MYSQL_IMPORT_PASSWORD="${TF_VAR_mysql_admin_password}"
```

O usuário administrativo do MySQL HeatWave é usado apenas para provisionamento e importação do SQL.

Após a importação, o script `004_import_sql.sh` gera automaticamente o arquivo `scripts/db.env` com os valores finais de conexão da aplicação e credenciais.

### Pré-requisito para importar o SQL

Para executar a importação, é necessário ter um cliente MySQL disponível.

O script `004_import_sql.sh` pode usar:

1. `mysql`, se estiver instalado localmente;
2. `mariadb`, se estiver instalado localmente;

```bash
mysql --version
```

### Executar o import

Depois de executar o Terraform e o build das imagens, rode:

```bash
./scripts/004_import_sql.sh
```

Esse script deve:

1. ler os outputs do Terraform;
2. obter o endpoint público do MySQL HeatWave;
3. aguardar o banco aceitar conexões;
4. importar o arquivo SQL configurado em `SQL_FILE_PATH`;
5. gerar o arquivo `scripts/db.env`.

O arquivo gerado deve ter conteúdo parecido com:

```bash
export DB_URL="jdbc:mysql://<mysql-public-ip>:3306/copa_ticketing_demo?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
export DB_USER="app"
export DB_PASS='CopaTicketing#2026_App!9Qx'
```

Esse arquivo é gerado automaticamente. Não edite manualmente e não versione no Git.

### Uso pelo deploy Kubernetes

O script `03_deploy_k8s.sh` deve carregar automaticamente o arquivo:

```text
scripts/db.env
```

Assim, as variáveis abaixo serão usadas nos manifests renderizados:

```bash
DB_URL
DB_USER
DB_PASS
```

Por isso, a ordem correta de execução é:

```bash
./scripts/01_apply_terraform.sh
./scripts/02_build_and_push_images.sh
./scripts/004_import_sql.sh
./scripts/03_deploy_k8s.sh
```

## 9. Deploy no Kubernetes

Execute:

```bash
./scripts/03_deploy_k8s.sh
```

Esse script deve:

1. gerar `generated/kubeconfig` a partir do output do Terraform;
2. carregar `.env`;
3. carregar `scripts/images.env`;
4. criar ou atualizar o secret de pull do OCIR;
5. renderizar os templates Kubernetes em `generated/k8s`;
6. aplicar os manifests;
7. forcar novo rollout, se `FORCE_ROLLOUT_RESTART=true`;
8. aguardar os deployments ficarem prontos;
9. criar ou atualizar o `Service` publico `copa-frontend-lb`.

## 10. Testar a aplicacao

Configure o kubeconfig na pasta infra:

```bash
export KUBECONFIG="$(pwd)/generated/kubeconfig"
```

Veja os pods:

```bash
kubectl -n "$K8S_NAMESPACE" get pods -o wide
```

Veja o Load Balancer:

```bash
kubectl -n "$K8S_NAMESPACE" get svc copa-frontend-lb
```

Aguarde o campo `EXTERNAL-IP` ser preenchido.

Exemplo:

```text
NAME               TYPE           CLUSTER-IP      EXTERNAL-IP      PORT(S)
copa-frontend-lb   LoadBalancer   10.96.253.46    163.176.223.24   80:30385/TCP
```

A URL pública será por exemplo:

```text
http://163.176.223.24/
```

Use a porta 80. Não use a porta `30385`; essa é a NodePort interna criada pelo Kubernetes.

Teste via terminal:

```bash
curl -I http://<EXTERNAL-IP>/
```

Teste o backend de dentro do cluster:

```bash
kubectl -n "$K8S_NAMESPACE" run curl-backend \
  --rm -it \
  --image=curlimages/curl:8.10.1 \
  --restart=Never \
  -- http://copa-backend:8080/health
```

## 11. Destruir o ambiente

Execute:

```bash
source .env
./scripts/99_destroy.sh
```

O script de destroy deve remover os recursos Kubernetes quando possivel e depois executar `terraform destroy`.
