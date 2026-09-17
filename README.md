# NeuroPlan CI/CD Automation

1차 프로젝트에서 사용하는 NeuroPlan 애플리케이션의 CI/CD 자동화 구성입니다.

Ansible을 이용해 Jenkins, Docker, kubectl, Helm, Argo CD를 자동 구성하고,
GitHub의 애플리케이션 변경사항을 Kubernetes까지 자동 배포하도록 구성했습니다.

---

## CI/CD Architecture

```text
Developer Git Push
        │
        ▼
      GitHub
        │
        ▼
      Jenkins
        │
        ├── Frontend 변경 감지
        └── Backend 변경 감지
        │
        ▼
   Docker Build
        │
        ▼
Harbor Registry Push
harbor.nplan.local:80
        │
        ▼
 Git SHA Image Tag
        │
        ▼
Kustomize newTag Update
        │
        ▼
 Git Commit / Push
        │
        ▼
 Argo CD Auto Sync
        │
        ▼
Kubernetes Rolling Deployment
```

---

## Application Repository

GitHub Repository:

```text
Infrastructure-hybrid09/onprem-k8s-application-devopsVM
```

애플리케이션 주요 구조:

```text
neuroplan-login-mvp/
├── frontend/
├── backend/
├── k8s/
│   ├── base/
│   └── onprem/
└── Jenkinsfile
```

Argo CD가 사용하는 Kustomize 경로:

```text
neuroplan-login-mvp/k8s/onprem
```

---

## Container Registry

온프레미스 Harbor Private Container Registry를 사용합니다.

```text
harbor.nplan.local:80
```

사용 이미지:

```text
harbor.nplan.local:80/neuroplan/frontend
harbor.nplan.local:80/neuroplan/backend
```

이미지 태그는 애플리케이션 Git Commit의 **7자리 Short SHA**를 사용합니다.

예:

```text
harbor.nplan.local:80/neuroplan/frontend:9935cf5
harbor.nplan.local:80/neuroplan/backend:9935cf5
```

---

## Jenkins CI

Jenkins Job:

```text
neuroplan-login-mvp-ci
```

Jenkins는 GitHub Repository를 Polling하여 변경사항을 확인합니다.

변경된 경로를 기준으로 필요한 애플리케이션만 선택적으로 Build합니다.

```text
Frontend 변경
→ Frontend Build / Push

Backend 변경
→ Backend Build / Push

Frontend + Backend 변경
→ Frontend + Backend Build / Push

Kubernetes Manifest만 변경
→ Application Image Build Skip
```

Docker Image Push가 완료되면 Jenkins가 Main과 DR 배포 경로의 `newTag`를 자동으로 변경합니다.

```text
neuroplan-login-mvp/k8s/onprem/kustomization.yaml
neuroplan-login-mvp/k8s/dr/kustomization.yaml
```

변경된 `kustomization.yaml`은 Jenkins가 다시 GitHub의 `main` Branch에 Commit / Push합니다.

Jenkins가 자신이 생성한 Kustomize Commit을 다시 감지하더라도 Frontend / Backend 소스 변경이 없으면 Image Build를 수행하지 않습니다.

```text
Frontend changed : false
Backend changed  : false
```

이를 통해 Jenkins의 자체 Commit으로 인한 불필요한 재빌드를 방지합니다.

---

## Argo CD Continuous Delivery

Argo CD Application:

```text
Main : neuroplan-login-mvp
DR   : neuroplan-login-mvp-dr
```

각 Application은 다음 GitOps 경로를 Desired State로 사용합니다.

```text
Main : neuroplan-login-mvp/k8s/onprem
DR   : neuroplan-login-mvp/k8s/dr
```

Main Argo CD에 DR K3s Cluster를 등록하고,
Argo CD Auto Sync를 통해 GitOps Repository의 Kustomize Image Tag 변경사항을 각 환경에 자동 반영합니다.

전체 CD 흐름:

```text
Jenkins Image Build / Harbor Push
        |
        v
GitOps onprem / dr newTag 변경
        |
        v
Git Commit / Push
        |
        v
Argo CD Auto Sync
     /        \
    v          v
Main K8s    DR K3s
```

---

## Ansible Structure

CI/CD 관련 주요 Ansible 구조:

```text
ansible-project/
├── inventory/
│   └── group_vars/
│
├── playbooks/
│   ├── cicd.yml
│   ├── devops.yml
│   ├── harbor.yml
│   ├── harbor-bootstrap.yml
│   ├── harbor-registry.yml
│   ├── harbor-pull-secret.yml
│   ├── k3s-argocd-access.yml
│   ├── argocd-dr.yml
│   ├── argocd-dr-application.yml
│   ├── dr-application-secrets.yml
│   └── argocd-sync.yml
│
└── roles/
    ├── docker/
    ├── jenkins/
    ├── kubectl_client/
    ├── helm/
    ├── argocd/
    ├── harbor/
    ├── harbor_bootstrap/
    ├── harbor_registry/
    ├── harbor_pull_secret/
    ├── k3s_argocd_access/
    ├── argocd_dr_cluster/
    ├── argocd_dr_application/
    └── dr_application_secrets/
```

### 역할

```text
docker
→ Docker Engine 및 Harbor HTTP Registry 사용 설정

jenkins
→ Jenkins 설치 및 CI Job / Credential 구성

kubectl_client
→ Jenkins에서 사용할 kubectl 설치

helm
→ Helm Client 설치

argocd
→ Argo CD 설치 및 Application 구성
```

---

## DevOps CI/CD Configuration

Jenkins, Docker, kubectl, Helm, Argo CD 등의 DevOps 환경을 Ansible로 구성합니다.

```bash
ansible-playbook \
  -i inventory/hosts.ini \
  playbooks/devops.yml \
  -K \
  --ask-vault-pass
```

---

## Argo CD Configuration

Main Argo CD 설치 및 기본 Application 구성은 `devops.yml`에 포함되어 있습니다.

```bash
ansible-playbook \
  -i inventory/hosts.ini \
  playbooks/devops.yml \
  -K \
  --ask-vault-pass
```

DR K3s 연동은 다음 Playbook으로 구성합니다.

```text
playbooks/k3s-argocd-access.yml
playbooks/argocd-dr.yml
playbooks/argocd-dr-application.yml
```

전체 CI/CD 구성을 순서대로 적용할 경우:

```bash
ansible-playbook \
  -i inventory/hosts.ini \
  playbooks/cicd.yml \
  -K \
  --ask-vault-pass
```

`argocd-sync.yml`은 Argo CD 동기화 확인 및 운영 시 사용하는 보조 Playbook입니다.

---

## Secrets

민감정보는 Ansible Vault를 사용해 관리합니다.

주요 Vault 파일:

```text
inventory/group_vars/all/vault.yml
inventory/group_vars/minio_nodes/vault.yml
```

관리 대상 예:

```text
GitHub SSH Private Key
Harbor Robot Account Credential
Jenkins Credential Secret
MinIO Root Credential
```

`inventory/group_vars/all/vault.yml`은 `.gitignore`를 통해 Git Repository에서 제외합니다.

일부 환경별 Vault 파일은 Ansible Vault로 암호화한 상태로 Git에서 관리합니다.

Vault 파일은 다음과 같은 형식으로 암호화되어 있습니다.

```text
$ANSIBLE_VAULT;1.1;AES256
```

Vault 비밀번호는 Repository에 저장하지 않고 Playbook 실행 시 직접 입력합니다.

```text
--ask-vault-pass
```

Private Key, Token, Password 등의 민감정보를 일반 변수 파일이나 Git Repository에 평문으로 저장하지 않습니다.

---

## CI/CD Validation

실제 NeuroPlan 애플리케이션을 이용하여 CI/CD End-to-End 테스트를 수행했습니다.

### Frontend 단독 변경

```text
Frontend changed : true
Backend changed  : false
```

결과:

```text
Frontend Build
→ Harbor Registry Push
→ Kustomize newTag 변경
→ Argo CD Auto Sync
→ Kubernetes Deployment 성공
```

### Backend 단독 변경

```text
Frontend changed : false
Backend changed  : true
```

결과:

```text
Backend Build
→ Harbor Registry Push
→ Kustomize newTag 변경
→ Argo CD Auto Sync
→ Kubernetes Deployment 성공
```

### Frontend + Backend 동시 변경

```text
Frontend changed : true
Backend changed  : true
```

결과:

```text
Frontend Build / Push
Backend Build / Push
        │
        ▼
동일 Git SHA 기반 Image Tag 생성
        │
        ▼
Kustomize newTag 변경
        │
        ▼
Argo CD Auto Sync
        │
        ▼
Kubernetes Deployment 성공
```

최종 배포 검증 예:

```text
neuroplan-backend
→ harbor.nplan.local:80/neuroplan/backend:9935cf5

neuroplan-frontend
→ harbor.nplan.local:80/neuroplan/frontend:9935cf5
```

Argo CD:

```text
SYNC     HEALTH
Synced   Healthy
```

---

## Validation Result

검증 완료 항목:

- Frontend 단독 변경 감지 및 배포
- Backend 단독 변경 감지 및 배포
- Frontend + Backend 동시 변경 및 배포
- 변경된 애플리케이션만 선택적 Build
- Harbor Registry Image Push
- Git Commit SHA 기반 Image Tag
- Kustomize `newTag` 자동 갱신
- Jenkins Git Commit / Push
- Jenkins 자체 Commit 재감지 시 Build Skip
- Argo CD Auto Sync
- Kubernetes Rolling Deployment
- Argo CD `Synced / Healthy` 확인

---

## Final CI/CD Flow

```text
Developer
   │
   │ Git Push
   ▼
GitHub
   │
   │ SCM Polling
   ▼
Jenkins
   │
   ├── Frontend Change Detection
   └── Backend Change Detection
   │
   ▼
Docker Build
   │
   ▼
Harbor Registry
harbor.nplan.local:80
   │
   ▼
Git SHA Image Tag
   │
   ▼
Kustomize newTag Update
   │
   ▼
Git Commit / Push
   │
   ▼
Argo CD
   │
   │ Auto Sync
   ▼
Kubernetes
   │
   ▼
Rolling Deployment
```
