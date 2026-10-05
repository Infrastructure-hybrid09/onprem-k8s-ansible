# Ansible Playbooks

InfraReady 온프레미스 Kubernetes 프로젝트의 인프라 구성, CI/CD, DR, 모니터링 및 상태 점검을 실행하는 Ansible Playbook 모음입니다.

`playbooks/`는 실제 실행 단위이며, 대부분의 Playbook은 `roles/`에 정의된 Role을 대상 Inventory Group에 적용합니다. 일부 Health Check Playbook은 별도 Role 없이 점검 Task를 직접 포함합니다.

---

## 주요 구성

- 전체 노드 공통 설정 및 `/etc/hosts` 표준화
- DNS Client 및 NTP Client 구성
- Kubernetes 노드 사전 설정
- DevOps CI/CD 환경 구성
- Harbor Registry 및 Image Pull 환경 구성
- Main Kubernetes와 DR k3s의 Argo CD 연동
- DR k3s 및 MinIO 구성
- VM / Kubernetes / DR 모니터링 구성
- LB / DB / NFS / Infra Health Check
- 전체 CI/CD 구성 순차 실행

---

## Playbook 구성

### 공통 인프라

| Playbook | 대상 | Role | 역할 |
|---|---|---|---|
| [`common.yml`](common.yml) | `project_nodes` | `common` | 호스트명, 타임존, 공통 패키지, SSHD 등 기본 시스템 설정 |
| [`hosts.yml`](hosts.yml) | `project_nodes` | `hosts_config` | 프로젝트 표준 `/etc/hosts` 배포 |
| [`dns-client.yml`](dns-client.yml) | LB / DB / NFS / DevOps / MinIO / k3s / Monitoring | `dns_client` | Management Network의 DNS 서버 및 Search Domain 설정 |
| [`ntp-client.yml`](ntp-client.yml) | `all:!infra_nodes` | `ntp` | 전체 관리 노드를 Infra NTP 서버에 동기화 |
| [`infra_check.yml`](infra_check.yml) | `infra_nodes` | `infra_check` | BIND DNS 및 Chrony NTP 상태 점검 |

### Kubernetes / DR

| Playbook | 대상 | Role | 역할 |
|---|---|---|---|
| [`k8s-common.yml`](k8s-common.yml) | `control_plane`, `workers` | `k8s_common` | Kubernetes 노드 사전 설정 적용 |
| [`k3s.yml`](k3s.yml) | `k3s_nodes` | `k3s`, `k3s_argocd_access` | DR k3s 설치 및 Argo CD 접근 준비 |
| [`k3s-argocd-access.yml`](k3s-argocd-access.yml) | `k3s_nodes` | `k3s_argocd_access` | Main Argo CD가 DR k3s에 접근할 수 있도록 설정 |
| [`dr-application-secrets.yml`](dr-application-secrets.yml) | `k3s_nodes` | `dr_application_secrets` | DR 애플리케이션에 필요한 Secret 구성 |

### DevOps / CI/CD

| Playbook | 대상 | Role | 역할 |
|---|---|---|---|
| [`devops.yml`](devops.yml) | `devops_nodes` | `java`, `maven`, `nodejs`, `docker`, `kubectl_client`, `jenkins`, `helm`, `argocd` | Jenkins 기반 CI와 Argo CD 기반 CD 환경 구성 |
| [`cicd.yml`](cicd.yml) | 여러 Playbook 순차 실행 | - | Harbor → DevOps → Registry → Secret → DR Argo CD 연동까지 전체 CI/CD 구성 |
| [`argocd-sync.yml`](argocd-sync.yml) | `devops_nodes` | 직접 Task | Main Argo CD Application 최초 Sync 수행 및 `Synced:Healthy` 대기 |

### Harbor Registry

| Playbook | 대상 | Role | 역할 |
|---|---|---|---|
| [`harbor.yml`](harbor.yml) | `minio_nodes` | `docker`, `harbor` | Harbor Registry 서버 구성 |
| [`harbor-bootstrap.yml`](harbor-bootstrap.yml) | `minio_nodes` | `harbor_bootstrap` | Harbor Project, Robot Account, 관리자 계정 구성 |
| [`harbor-registry.yml`](harbor-registry.yml) | `workers`, `k3s_nodes` | `harbor_registry` | Main Kubernetes 및 DR k3s의 Harbor Registry Runtime 설정 |
| [`harbor-pull-secret.yml`](harbor-pull-secret.yml) | `devops_nodes`, `k3s_nodes` | `harbor_pull_secret` | Main Kubernetes 및 DR k3s에 Image Pull Secret 적용 |

### Argo CD / GitOps DR 연동

| Playbook | 대상 | Role | 역할 |
|---|---|---|---|
| [`argocd-dr.yml`](argocd-dr.yml) | `k3s_nodes` | `argocd_dr_cluster` | DR k3s Cluster를 Main Argo CD에 등록 |
| [`argocd-dr-application.yml`](argocd-dr-application.yml) | `localhost` | `argocd_dr_application` | DR용 Argo CD Application 구성 |
| [`argocd-sync.yml`](argocd-sync.yml) | `devops_nodes` | 직접 Task | Main Application 최초 동기화 및 상태 확인 |

### Monitoring

| Playbook | 대상 | Role | 역할 |
|---|---|---|---|
| [`monitoring.yml`](monitoring.yml) | `monitoring_vm_nodes`, `devops_nodes` | `node_exporter_vm`, `node_exporter_k8s` | VM Node Exporter 설치 및 Kubernetes Node Exporter DaemonSet 배포 |
| [`k3s-monitoring.yml`](k3s-monitoring.yml) | `k3s_nodes` | `k3s_monitoring` | DR k3s의 Node Exporter, kube-state-metrics 및 외부 Prometheus 접근 구성 |
| [`minio-monitoring.yml`](minio-monitoring.yml) | `minio_nodes` | `node_exporter_vm` | MinIO VM에 Node Exporter 설치 |

### Storage

| Playbook | 대상 | Role | 역할 |
|---|---|---|---|
| [`minio.yml`](minio.yml) | `minio_nodes` | `minio` | 고정 버전 MinIO Community 설치 및 서비스 구성 |

### Health Check

| Playbook | 대상 | 점검 항목 |
|---|---|---|
| [`lb_check.yml`](lb_check.yml) | `loadbalancers` | HAProxy / Keepalived, API VIP, Service VIP, Main Kubernetes Backend, DR k3s Backup Backend |
| [`db_check.yml`](db_check.yml) | `database`, `devops_nodes` | MariaDB 서비스, Replication, MaxScale 상태 |
| [`nfs_check.yml`](nfs_check.yml) | `nfs_nodes` | NFS 서비스, `/etc/exports`, TCP 2049, 백업 공유 디렉터리 |
| [`infra_check.yml`](infra_check.yml) | `infra_nodes` | BIND, DNS 53/TCP·UDP, `nplan.local`, Chrony, NTP 123/UDP, 시간 동기화 |

---

## 전체 CI/CD 실행 흐름

`cicd.yml`은 개별 Playbook을 다음 순서로 실행합니다.

```text
1. harbor.yml
   │
   ▼
2. harbor-bootstrap.yml
   │
   ▼
3. devops.yml
   │
   ▼
4. harbor-registry.yml
   │
   ▼
5. harbor-pull-secret.yml
   │
   ▼
6. dr-application-secrets.yml
   │
   ▼
7. k3s-argocd-access.yml
   │
   ▼
8. argocd-dr.yml
   │
   ▼
9. argocd-dr-application.yml
```

역할 기준으로 보면 다음과 같습니다.

```text
Harbor Registry
      │
      ▼
Harbor Project / Robot Accounts
      │
      ▼
Jenkins / Docker / kubectl / Helm / Argo CD
      │
      ▼
Main K8s + DR k3s Harbor Registry 설정
      │
      ▼
Image Pull Secret
      │
      ▼
DR Application Secret
      │
      ▼
DR k3s Argo CD 접근 준비
      │
      ▼
Main Argo CD에 DR Cluster 등록
      │
      ▼
DR Argo CD Application 생성
```

---

## DevOps 구성

`devops.yml`은 `devops_nodes` 그룹에 다음 Role을 순서대로 적용합니다.

```text
java
  ↓
maven
  ↓
nodejs
  ↓
docker
  ↓
kubectl_client
  ↓
jenkins
  ↓
helm
  ↓
argocd
```

실행 예시:

```bash
ansible-playbook \
  -i inventory/hosts.ini \
  playbooks/devops.yml \
  -K \
  --ask-vault-pass
```

---

## 전체 CI/CD 구성 실행

Harbor부터 Jenkins, Argo CD, Main Kubernetes / DR 연동까지 전체 CI/CD 구성을 적용할 경우 실행합니다.

```bash
ansible-playbook \
  -i inventory/hosts.ini \
  playbooks/cicd.yml \
  -K \
  --ask-vault-pass
```

`cicd.yml`은 여러 Playbook을 순서대로 Import하므로 중간 단계만 다시 실행해야 하는 경우에는 해당 개별 Playbook을 직접 실행할 수 있습니다.

---

## 공통 인프라 실행

### 기본 시스템 설정

```bash
ansible-playbook -i inventory/hosts.ini playbooks/common.yml -K
```

### `/etc/hosts` 배포

```bash
ansible-playbook -i inventory/hosts.ini playbooks/hosts.yml -K
```

### DNS Client 구성

```bash
ansible-playbook -i inventory/hosts.ini playbooks/dns-client.yml -K
```

### NTP Client 구성

```bash
ansible-playbook -i inventory/hosts.ini playbooks/ntp-client.yml -K
```

---

## Kubernetes / DR 실행

### Kubernetes 노드 사전 설정

```bash
ansible-playbook -i inventory/hosts.ini playbooks/k8s-common.yml -K
```

### DR k3s 구성

```bash
ansible-playbook \
  -i inventory/hosts.ini \
  playbooks/k3s.yml \
  -K \
  --ask-vault-pass
```

### DR k3s를 Main Argo CD와 연결

```bash
ansible-playbook -i inventory/hosts.ini playbooks/k3s-argocd-access.yml -K --ask-vault-pass
ansible-playbook -i inventory/hosts.ini playbooks/argocd-dr.yml -K --ask-vault-pass
ansible-playbook -i inventory/hosts.ini playbooks/argocd-dr-application.yml --ask-vault-pass
```

### Main Argo CD 최초 Sync

```bash
ansible-playbook \
  -i inventory/hosts.ini \
  playbooks/argocd-sync.yml \
  --ask-vault-pass
```

`argocd-sync.yml`은 현재 다음 값을 직접 사용합니다.

```text
Application : neuroplan-login-mvp
Namespace   : argocd
Kubeconfig  : /home/devops/.kube/config
```

Sync 요청 시 `prune: false`를 사용하며 Application이 `Synced:Healthy`가 될 때까지 대기합니다.

---

## Harbor 구성

Harbor 관련 Playbook은 서버 설치, 초기 계정/Project 구성, Runtime Registry 설정, Kubernetes Secret 적용 단계로 분리되어 있습니다.

```text
harbor.yml
    ↓
harbor-bootstrap.yml
    ↓
harbor-registry.yml
    ↓
harbor-pull-secret.yml
```

개별 실행 예시:

```bash
ansible-playbook -i inventory/hosts.ini playbooks/harbor.yml -K --ask-vault-pass
ansible-playbook -i inventory/hosts.ini playbooks/harbor-bootstrap.yml --ask-vault-pass
ansible-playbook -i inventory/hosts.ini playbooks/harbor-registry.yml -K --ask-vault-pass
ansible-playbook -i inventory/hosts.ini playbooks/harbor-pull-secret.yml -K --ask-vault-pass
```

---

## Monitoring 실행

### Main 환경 Node Exporter

```bash
ansible-playbook -i inventory/hosts.ini playbooks/monitoring.yml -K
```

`monitoring.yml`은 두 영역을 구성합니다.

```text
Non-Kubernetes VM
→ node_exporter_vm

Main Kubernetes
→ node_exporter_k8s DaemonSet
```

### DR k3s Monitoring

```bash
ansible-playbook -i inventory/hosts.ini playbooks/k3s-monitoring.yml -K
```

DR k3s에는 Node Exporter와 kube-state-metrics를 배포하고 외부 Prometheus가 메트릭을 수집할 수 있도록 접근 구성을 적용합니다.

### MinIO Monitoring

```bash
ansible-playbook -i inventory/hosts.ini playbooks/minio-monitoring.yml -K
```

---

## Health Check 실행

Health Check Playbook은 운영 상태 점검을 목적으로 사용합니다.

### Load Balancer

```bash
ansible-playbook -i inventory/hosts.ini playbooks/lb_check.yml -K
```

주요 점검 항목:

```text
HAProxy / Keepalived
API VIP 192.168.34.100:6443
Service VIP 192.168.24.100:443
CP1 / CP2 / CP3 API Backend
Worker1 / Worker2 / Worker3 NGF Backend
DR k3s 192.168.34.71:30443 Backup Backend
```

### Database

```bash
ansible-playbook -i inventory/hosts.ini playbooks/db_check.yml -K
```

주요 점검 항목:

```text
MariaDB Service
TCP 3306
Primary / Replica
Replication IO / SQL
MaxScale
```

### NFS

```bash
ansible-playbook -i inventory/hosts.ini playbooks/nfs_check.yml -K
```

주요 점검 항목:

```text
nfs-server Service
TCP 2049
/etc/exports
/backup/etcd
/backup/config
/backup/mariadb
```

### Infra DNS / NTP

```bash
ansible-playbook -i inventory/hosts.ini playbooks/infra_check.yml -K
```

주요 점검 항목:

```text
BIND Service
DNS TCP / UDP 53
nplan.local Resolution
Chrony Service
NTP UDP 123
NTP Synchronization
```

---

## Check Mode

변경 전 적용 계획을 확인하려면 지원되는 Playbook에 `--check` 옵션을 사용할 수 있습니다.

```bash
ansible-playbook \
  -i inventory/hosts.ini \
  playbooks/common.yml \
  --check
```

Role 또는 직접 Task에서 `check_mode: false`를 사용하는 조회 작업은 Check Mode에서도 실행될 수 있습니다. 따라서 Check Mode 지원 범위는 연결된 Role 및 Playbook 구현에 따라 다릅니다.

---

## Vault

민감정보가 필요한 Playbook은 Ansible Vault를 사용합니다.

```bash
--ask-vault-pass
```

대표적으로 Harbor Credential, Jenkins Credential, GitHub 인증정보, MinIO Credential, DR Secret 관련 값이 Vault 변수로 관리됩니다.

실행 예시:

```bash
ansible-playbook \
  -i inventory/hosts.ini \
  playbooks/cicd.yml \
  -K \
  --ask-vault-pass
```

Vault 비밀번호와 민감정보는 일반 Playbook 파일에 평문으로 저장하지 않습니다.

---

## Playbook과 Role 관계

```text
playbooks/
   │
   ├── 실행 대상 Inventory Group 정의
   ├── become / gather_facts 등 실행 조건 정의
   ├── Role 실행 순서 정의
   └── 일부 운영 Health Check Task 직접 수행
          │
          ▼
roles/
   │
   ├── tasks/
   ├── defaults/
   ├── handlers/
   └── templates/
```

Playbook은 **어디에 무엇을 실행할지**를 정의하고, Role은 **실제로 어떤 설정을 적용할지**를 정의합니다.

---

## 주요 실행 단위 정리

```text
Base Infrastructure
├── common.yml
├── hosts.yml
├── dns-client.yml
└── ntp-client.yml

Kubernetes / DR
├── k8s-common.yml
├── k3s.yml
├── k3s-argocd-access.yml
└── dr-application-secrets.yml

CI/CD
├── devops.yml
├── harbor.yml
├── harbor-bootstrap.yml
├── harbor-registry.yml
├── harbor-pull-secret.yml
├── argocd-dr.yml
├── argocd-dr-application.yml
├── argocd-sync.yml
└── cicd.yml

Monitoring
├── monitoring.yml
├── k3s-monitoring.yml
└── minio-monitoring.yml

Storage
└── minio.yml

Health Check
├── lb_check.yml
├── db_check.yml
├── nfs_check.yml
└── infra_check.yml
```

---

## 관련 디렉터리

- [`../roles/`](../roles/) - Playbook에서 호출하는 Ansible Role
- [`../inventory/`](../inventory/) - 호스트 및 Group Variable 정의
- [`../README.md`](../README.md) - 전체 NeuroPlan CI/CD 자동화 구성

애플리케이션 CI 파이프라인은 별도 Application Repository의 `Jenkinsfile`에서 관리하며, Ansible Playbook은 Jenkins / Harbor / Argo CD 및 관련 인프라 구성을 담당합니다.
