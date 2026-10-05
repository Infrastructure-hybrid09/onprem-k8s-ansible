# DR Argo CD Application 구성

InfraReady 온프레미스 환경에서 **Main Argo CD가 DR k3s 클러스터의 NeuroPlan 애플리케이션을 GitOps 방식으로 관리하도록 DR 전용 Argo CD Application을 구성하는 Ansible Role**입니다.

이 Role은 Main Argo CD의 기존 Application 정보를 기준으로 동일한 AppProject를 사용하고, 사전에 등록된 DR k3s Cluster Secret을 확인한 뒤 DR 전용 `Application` 리소스를 생성합니다.

DR k3s 자체 설치와 Argo CD 접근 권한 구성, Main Argo CD에 DR Cluster를 등록하는 작업은 각각 별도 Role에서 담당합니다.

---

## 주요 기능

- DR Argo CD Application 필수 변수 검증
- 기존 Main Application 존재 여부 확인
- Main Application이 사용하는 AppProject 조회
- DR Application에 Main과 동일한 AppProject 적용
- DR k3s Cluster 등록 Secret 존재 및 유형 검증
- Jinja2 기반 DR Argo CD Application Manifest 생성
- `kubectl apply -f -` 방식으로 Application 적용
- DR Application 생성 여부 최종 검증
- GitOps Source Repository / Branch / Path 지정
- DR k3s API Server 및 `application` Namespace를 Destination으로 지정
- Argo CD Auto Sync 구성
- `prune` 및 `selfHeal` 활성화
- Check Mode에서 실제 Application 생성 없이 구성 계획 표시

---

## Role 구성

```text
roles/argocd_dr_application/
├── README.md
├── defaults/
│   └── main.yml
├── handlers/
│   └── main.yml
├── meta/
│   └── main.yml
├── tasks/
│   └── main.yml
├── templates/
│   └── application.yaml.j2
└── vars/
    └── main.yml
```

| 파일 | 역할 |
|---|---|
| [defaults/main.yml](defaults/main.yml) | DR Application 이름, Git Repository, 배포 경로, Destination 및 Auto Sync 기본값 정의 |
| [tasks/main.yml](tasks/main.yml) | Main Application 확인, DR Cluster 등록 검증, DR Application 생성 및 최종 검증 |
| [templates/application.yaml.j2](templates/application.yaml.j2) | DR Argo CD Application Manifest 생성 |
| [handlers/main.yml](handlers/main.yml) | 현재 별도 Handler 없음 |
| [vars/main.yml](vars/main.yml) | 현재 별도 Role 변수 없음 |
| [meta/main.yml](meta/main.yml) | Ansible Role 메타데이터 |

---

## 현재 프로젝트 구성

현재 Role 기본값은 다음과 같습니다.

| 항목 | 설정 |
|---|---|
| DR Application | `neuroplan-login-mvp-dr` |
| Main Application | `neuroplan-login-mvp` |
| Argo CD Namespace | `argocd` |
| Git Repository | `https://github.com/Infrastructure-hybrid09/onprem-k8s-application-devopsVM.git` |
| Target Revision | `main` |
| DR GitOps Path | `neuroplan-login-mvp/k8s/dr` |
| DR Destination Server | `https://192.168.34.71:6443` |
| DR Destination Namespace | `application` |
| DR Cluster Secret | `cluster-dr-k3s` |
| Auto Sync | 활성화 |
| Prune | 활성화 |
| Self Heal | 활성화 |

---

## DR Application 구조

최종적으로 생성되는 Application의 논리 구조는 다음과 같습니다.

```text
Main Argo CD
Namespace: argocd
        │
        ▼
Application
neuroplan-login-mvp-dr
        │
        ├── Source
        │   ├── Repository
        │   │   └── Infrastructure-hybrid09/
        │   │       onprem-k8s-application-devopsVM
        │   ├── Revision: main
        │   └── Path:
        │       neuroplan-login-mvp/k8s/dr
        │
        └── Destination
            ├── Server:
            │   https://192.168.34.71:6443
            └── Namespace:
                application
```

---

## 실행 전 준비

이 Role을 실행하기 전에 다음 구성이 완료되어 있어야 합니다.

### 1. Main Argo CD

Main Kubernetes에 Argo CD가 설치되어 있어야 하며 다음 Namespace를 사용할 수 있어야 합니다.

```text
argocd
```

### 2. Main Application

다음 Main Application이 이미 존재해야 합니다.

```text
neuroplan-login-mvp
```

Role은 Main Application에서 현재 사용 중인 AppProject를 조회합니다.

```bash
kubectl -n argocd get application neuroplan-login-mvp \
  -o jsonpath={.spec.project}
```

조회한 값은 DR Application의 `spec.project`에 그대로 사용합니다.

Main Application의 Project 값이 비어 있는 경우 Role 내부에서는 `default`를 사용하도록 구성되어 있습니다.

### 3. DR k3s Cluster 등록

DR k3s가 Main Argo CD에 사전에 등록되어 있어야 합니다.

Role은 다음 Secret을 확인합니다.

```text
cluster-dr-k3s
```

Secret의 다음 Label 값이 `cluster`인지 검증합니다.

```text
argocd.argoproj.io/secret-type=cluster
```

DR Cluster 등록은 별도 [argocd_dr_cluster](../argocd_dr_cluster) Role에서 수행합니다.

### 4. kubectl

이 Role은 `localhost`에서 실행되며 `kubectl` 명령을 직접 사용합니다.

따라서 Ansible을 실행하는 서버의 현재 kubeconfig가 Main Kubernetes 및 Main Argo CD 리소스에 접근할 수 있어야 합니다.

---

## 주요 변수

### Application 기본 정보

| 변수 | 기본값 | 설명 |
|---|---|---|
| `argocd_dr_application_name` | `neuroplan-login-mvp-dr` | DR Argo CD Application 이름 |
| `argocd_main_application_name` | `neuroplan-login-mvp` | AppProject 정보를 읽어올 Main Application |
| `argocd_namespace` | `argocd` | Argo CD가 설치된 Namespace |

### GitOps Source

| 변수 | 기본값 | 설명 |
|---|---|---|
| `argocd_dr_repo_url` | `https://github.com/Infrastructure-hybrid09/onprem-k8s-application-devopsVM.git` | DR GitOps Source Repository |
| `argocd_dr_target_revision` | `main` | 추적할 Git Branch / Revision |
| `argocd_dr_path` | `neuroplan-login-mvp/k8s/dr` | DR Kustomize 배포 경로 |

### Destination

| 변수 | 기본값 | 설명 |
|---|---|---|
| `argocd_dr_destination_server` | `https://192.168.34.71:6443` | DR k3s Kubernetes API Server |
| `argocd_dr_destination_namespace` | `application` | NeuroPlan을 배포할 DR Namespace |
| `argocd_dr_cluster_secret_name` | `cluster-dr-k3s` | Main Argo CD에 등록된 DR Cluster Secret 이름 |

### Sync Policy

| 변수 | 기본값 | 설명 |
|---|---|---|
| `argocd_dr_auto_sync_enabled` | `true` | Argo CD Automated Sync 활성화 여부 |
| `argocd_dr_auto_sync_prune` | `true` | Git에서 제거된 리소스를 클러스터에서도 정리 |
| `argocd_dr_auto_sync_self_heal` | `true` | 실제 상태가 Git Desired State와 달라질 경우 자동 복구 |

---

## 필수 변수 검증

Role 실행 초기에 다음 변수가 비어 있지 않은지 검증합니다.

```text
argocd_dr_application_name
argocd_dr_repo_url
argocd_dr_target_revision
argocd_dr_path
argocd_dr_destination_server
```

값이 유효하지 않으면 다음 단계로 진행하지 않습니다.

---

## Main Application의 AppProject 재사용

이 Role은 DR Application의 Project를 별도로 고정하지 않고 Main Application에서 읽어옵니다.

흐름:

```text
Main Application
neuroplan-login-mvp
        │
        ▼
.spec.project 조회
        │
        ▼
argocd_dr_project 설정
        │
        ▼
DR Application
neuroplan-login-mvp-dr
spec.project에 동일 값 적용
```

Task에서 사용하는 조회 방식:

```bash
kubectl \
  -n argocd \
  get application \
  neuroplan-login-mvp \
  -o jsonpath={.spec.project}
```

Main Application에서 Project 값이 조회되면 해당 값을 사용하고, 값이 비어 있으면 다음 값을 사용합니다.

```text
default
```

이 방식으로 Main과 DR Application이 같은 Argo CD Project 정책을 사용할 수 있도록 구성합니다.

---

## DR Cluster 등록 검증

Application을 생성하기 전에 DR k3s가 Main Argo CD에 등록되어 있는지 확인합니다.

확인 대상:

```text
Secret: cluster-dr-k3s
Namespace: argocd
```

조회 항목:

```text
.metadata.labels.argocd.argoproj.io/secret-type
```

기대값:

```text
cluster
```

검증 흐름:

```text
cluster-dr-k3s Secret 조회
        │
        ▼
Command rc == 0 ?
        │
        ▼
secret-type == cluster ?
        │
        ├── Yes → DR Application 생성 진행
        │
        └── No  → 실행 중단
```

DR Cluster 등록이 완료되지 않은 경우 Role은 일반 실행에서 다음 단계로 진행하지 않습니다.

---

## Application Manifest

Role은 [templates/application.yaml.j2](templates/application.yaml.j2)를 이용해 Argo CD Application Manifest를 생성합니다.

현재 기본값을 기준으로 한 핵심 구조는 다음과 같습니다.

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application

metadata:
  name: neuroplan-login-mvp-dr
  namespace: argocd

spec:
  project: <Main Application과 동일한 AppProject>

  source:
    repoURL: https://github.com/Infrastructure-hybrid09/onprem-k8s-application-devopsVM.git
    targetRevision: main
    path: neuroplan-login-mvp/k8s/dr

  destination:
    server: https://192.168.34.71:6443
    namespace: application

  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

---

## Application 적용 방식

Role은 Manifest 파일을 별도로 저장한 뒤 적용하는 방식이 아니라 Ansible Template Lookup 결과를 표준 입력으로 전달합니다.

실제 실행 방식:

```text
application.yaml.j2
       │
       ▼
Ansible template lookup
       │
       ▼
kubectl apply -f -
       │
       ▼
DR Argo CD Application
```

Task에서 사용하는 핵심 명령:

```bash
kubectl apply -f -
```

Manifest 내용은 다음 Template Lookup 결과가 `stdin`으로 전달됩니다.

```text
lookup('ansible.builtin.template', 'application.yaml.j2')
```

---

## Auto Sync 정책

기본적으로 Automated Sync가 활성화되어 있습니다.

```yaml
argocd_dr_auto_sync_enabled: true
argocd_dr_auto_sync_prune: true
argocd_dr_auto_sync_self_heal: true
```

따라서 DR Application은 Git Repository의 다음 경로를 Desired State로 사용합니다.

```text
neuroplan-login-mvp/k8s/dr
```

동작 흐름:

```text
Git Repository
k8s/dr 변경
      │
      ▼
Argo CD 변경 감지
      │
      ▼
Auto Sync
      │
      ├── prune=true
      │
      └── selfHeal=true
      │
      ▼
DR k3s
application Namespace
```

### `prune`

Git Desired State에서 제거된 리소스를 실제 DR 클러스터에서도 정리하도록 설정합니다.

### `selfHeal`

DR 클러스터의 실제 상태가 Git에 정의된 Desired State와 달라질 경우 Argo CD가 자동으로 다시 맞추도록 설정합니다.

### Auto Sync 비활성화

다음 변수를 `false`로 설정하면 Template에서 `syncPolicy` 블록 자체를 생성하지 않습니다.

```yaml
argocd_dr_auto_sync_enabled: false
```

---

## 실행 방법

저장소 루트에서 다음 Playbook을 실행합니다.

```bash
ansible-playbook \
  -i inventory/hosts.ini \
  playbooks/argocd-dr-application.yml
```

[playbooks/argocd-dr-application.yml](../../playbooks/argocd-dr-application.yml)은 다음과 같이 `localhost`에서 Role을 실행합니다.

```yaml
- name: Configure DR Argo CD Application
  hosts: localhost
  connection: local
  gather_facts: false

  roles:
    - argocd_dr_application
```

원격 DR 서버에서 Application을 생성하는 것이 아니라, Main Kubernetes에 접근 가능한 Ansible 실행 서버에서 `kubectl`을 이용해 Argo CD Application CR을 생성합니다.

---

## 전체 CI/CD 구성에서의 실행 순서

전체 CI/CD Playbook에서는 DR 관련 작업이 다음 순서로 수행됩니다.

```text
6. dr-application-secrets.yml
        │
        ▼
7. k3s-argocd-access.yml
        │
        ▼
8. argocd-dr.yml
   DR k3s Cluster 등록
        │
        ▼
9. argocd-dr-application.yml
   DR Application 생성
```

즉 `argocd_dr_application` Role을 실행하기 전에 최소한 DR k3s 접근 구성과 Argo CD Cluster 등록이 완료되어 있어야 합니다.

---

## DR GitOps 전체 흐름

```text
Application Repository
Infrastructure-hybrid09/
onprem-k8s-application-devopsVM
        │
        │
        │ path:
        │ neuroplan-login-mvp/k8s/dr
        ▼
Main Argo CD
        │
        │ Application:
        │ neuroplan-login-mvp-dr
        ▼
DR Cluster Registration
cluster-dr-k3s
        │
        ▼
DR k3s API
https://192.168.34.71:6443
        │
        ▼
Namespace
application
        │
        ▼
NeuroPlan DR Workloads
```

---

## Check Mode

이 Role은 Ansible Check Mode를 구분하여 처리합니다.

실행 예:

```bash
ansible-playbook \
  -i inventory/hosts.ini \
  playbooks/argocd-dr-application.yml \
  --check
```

### Check Mode에서도 수행되는 확인

Main Application의 AppProject 조회는 실제 상태 확인을 위해 Check Mode에서도 실행됩니다.

DR Cluster Secret 조회 또한 실제 Main Argo CD 상태를 읽습니다.

### Check Mode에서 생략되는 작업

실제 DR Application 생성은 수행하지 않습니다.

대신 다음 형태의 구성 계획을 출력합니다.

```text
DR Argo CD Application 'neuroplan-login-mvp-dr'
would be configured for cluster 'https://192.168.34.71:6443'.
```

또한 Check Mode에서는 실제 Application 생성 후 검증 단계도 수행하지 않습니다.

---

## 생성 후 검증

일반 실행에서는 Application 생성 후 다음 리소스를 다시 조회합니다.

```bash
kubectl \
  -n argocd \
  get application \
  neuroplan-login-mvp-dr \
  -o name
```

기대 결과:

```text
application.argoproj.io/neuroplan-login-mvp-dr
```

결과가 일치하지 않으면 Role 실행을 실패 처리합니다.

---

## 추가 수동 검증

Role 실행 후 다음 명령으로 상태를 확인할 수 있습니다.

### Application 조회

```bash
kubectl -n argocd get application neuroplan-login-mvp-dr
```

### 상세 상태

```bash
kubectl -n argocd get application neuroplan-login-mvp-dr -o yaml
```

### Source 확인

```bash
kubectl -n argocd get application neuroplan-login-mvp-dr \
  -o jsonpath='{.spec.source.repoURL}{"\n"}{.spec.source.targetRevision}{"\n"}{.spec.source.path}{"\n"}'
```

기대값:

```text
https://github.com/Infrastructure-hybrid09/onprem-k8s-application-devopsVM.git
main
neuroplan-login-mvp/k8s/dr
```

### Destination 확인

```bash
kubectl -n argocd get application neuroplan-login-mvp-dr \
  -o jsonpath='{.spec.destination.server}{"\n"}{.spec.destination.namespace}{"\n"}'
```

기대값:

```text
https://192.168.34.71:6443
application
```

### Sync 상태 확인

```bash
kubectl -n argocd get application neuroplan-login-mvp-dr \
  -o jsonpath='{.status.sync.status}{" / "}{.status.health.status}{"\n"}'
```

운영 상태에서는 Application의 실제 Sync/Health 상태를 함께 확인합니다.

---

## Role 범위

이 `argocd_dr_application` Role은 **이미 Main Argo CD에 등록된 DR k3s 클러스터를 대상으로 DR Application을 구성하는 역할**만 담당합니다.

DR GitOps 구성은 다음 Role로 분리되어 있습니다.

| Role | 역할 |
|---|---|
| [k3s](../k3s) | DR k3s 설치 및 기본 클러스터 구성 |
| [k3s_argocd_access](../k3s_argocd_access) | Argo CD가 사용할 ServiceAccount/RBAC 구성 |
| [argocd_dr_cluster](../argocd_dr_cluster) | DR k3s Cluster를 Main Argo CD에 등록 |
| [argocd_dr_application](../argocd_dr_application) | DR 전용 Argo CD Application 생성 |

흐름:

```text
k3s
 │
 ▼
k3s_argocd_access
 │
 ▼
argocd_dr_cluster
 │
 ▼
argocd_dr_application
```

---

## Main / DR Application 관계

현재 프로젝트의 Main과 DR Application은 다음과 같이 구분됩니다.

| 구분 | Main | DR |
|---|---|---|
| Application | `neuroplan-login-mvp` | `neuroplan-login-mvp-dr` |
| Cluster | Main Kubernetes | DR k3s |
| GitOps Path | `neuroplan-login-mvp/k8s/onprem` | `neuroplan-login-mvp/k8s/dr` |
| Namespace | `application` | `application` |
| AppProject | Main Application 설정 | Main과 동일한 Project 사용 |

DR Role은 Main Application의 AppProject를 직접 조회해 재사용하므로 Project 정책을 별도로 중복 정의하지 않습니다.

---

## 관련 코드

- [DR Application Playbook](../../playbooks/argocd-dr-application.yml)
- [전체 CI/CD Playbook](../../playbooks/cicd.yml)
- [Main Argo CD Role](../argocd)
- [DR k3s Role](../k3s)
- [k3s Argo CD Access Role](../k3s_argocd_access)
- [DR Cluster Registration Role](../argocd_dr_cluster)
- [DR Application Secret Role](../dr_application_secrets)
- [애플리케이션 저장소](https://github.com/Infrastructure-hybrid09/onprem-k8s-application-devopsVM)

---

## 최종 구성

```text
Main Kubernetes
      │
      ▼
Main Argo CD
Namespace: argocd
      │
      ├── Main Application
      │   neuroplan-login-mvp
      │
      └── DR Application
          neuroplan-login-mvp-dr
                │
                ├── Source
                │   Repository:
                │   onprem-k8s-application-devopsVM
                │
                │   Revision:
                │   main
                │
                │   Path:
                │   neuroplan-login-mvp/k8s/dr
                │
                └── Destination
                    DR k3s
                    192.168.34.71:6443
                          │
                          ▼
                    Namespace
                    application
                          │
                          ▼
                    NeuroPlan DR
```
