# Ansible Roles 구성

InfraReady 1차 온프레미스 Kubernetes 환경과 DR k3s 환경을 자동화하기 위해 사용하는 Ansible Role 모음입니다.

공통 OS 설정부터 DevOps 도구, Harbor Registry, Argo CD GitOps, Kubernetes/DR, 모니터링, MinIO까지 기능 단위로 Role을 분리해 관리합니다. 각 Role은 필요한 설정만 독립적으로 적용할 수 있으며, 프로젝트 Playbook에서 조합해 전체 환경을 구성합니다.

## 주요 기능

- 전체 서버 공통 설정 및 `/etc/hosts`, DNS, NTP 클라이언트 구성
- Kubernetes 노드 공통 사전 설정
- Java, Maven, Node.js, Docker, kubectl, Helm 등 DevOps 도구 구성
- Jenkins CI 환경 및 Harbor Private Registry 구성
- Harbor Project, Robot Account, Registry 연결 및 Image Pull Secret 구성
- Argo CD 설치와 Main/DR GitOps Application 구성
- DR k3s 설치 및 Argo CD 연동
- VM/Kubernetes/DR 환경의 모니터링 Exporter 구성
- MinIO Object Storage 설치 및 서비스 검증
- DNS/NTP 인프라 상태 점검

## Role 구성

### 공통 시스템 및 네트워크

| Role | 역할 |
|---|---|
| [`common`](common/) | 호스트명, `Asia/Seoul` 타임존, 공통 패키지, SSH 서비스 등 기본 OS 환경 구성 |
| [`hosts_config`](hosts_config/) | Jinja2 템플릿을 이용해 표준화된 `/etc/hosts` 파일 배포 |
| [`dns_client`](dns_client/) | Management NIC를 기준으로 내부 DNS 서버와 `nplan.local` Search Domain을 적용하고 내부·외부 이름 해석 검증 |
| [`ntp`](ntp/) | Chrony 설치 및 중앙 NTP 서버 연동, 동기화 상태 검증 |
| [`infra_check`](infra_check/) | Infra 노드의 BIND/DNS 및 Chrony/NTP 서비스·포트·동기화 상태 점검 |

### DevOps 및 CI 환경

| Role | 역할 |
|---|---|
| [`java`](java/) | Jenkins 및 Java 기반 빌드에 필요한 Java Runtime/JDK 구성 |
| [`maven`](maven/) | Backend 빌드에 사용하는 Maven 구성 |
| [`nodejs`](nodejs/) | Frontend 빌드에 사용하는 Node.js 환경 구성 |
| [`docker`](docker/) | Docker Engine 구성 및 CI 빌드 환경 준비 |
| [`jenkins`](jenkins/) | Jenkins 설치와 CI Job, Plugin, Credential 등 자동화 구성 |
| [`kubectl_client`](kubectl_client/) | DevOps 호스트에서 Kubernetes를 관리하기 위한 `kubectl` Client 구성 |
| [`helm`](helm/) | Kubernetes 패키지 배포에 사용하는 Helm Client 구성 |

### Argo CD 및 GitOps

| Role | 역할 |
|---|---|
| [`argocd`](argocd/) | Helm을 이용한 Argo CD 설치·업그레이드와 Main Application Manifest 생성 및 적용 |
| [`k3s_argocd_access`](k3s_argocd_access/) | Main Argo CD가 DR k3s에 접근할 수 있도록 클러스터 접근 정보와 권한 준비 |
| [`argocd_dr_cluster`](argocd_dr_cluster/) | DR k3s 클러스터를 Main Argo CD 관리 대상 클러스터로 등록 |
| [`argocd_dr_application`](argocd_dr_application/) | DR GitOps 경로를 사용하는 Argo CD Application 구성 |
| [`dr_application_secrets`](dr_application_secrets/) | DR 애플리케이션 구동에 필요한 Kubernetes Secret 구성 |

Main과 DR의 GitOps 배포 경로는 애플리케이션 저장소의 Kustomize 구성을 기준으로 관리합니다.

```text
Main : neuroplan-login-mvp/k8s/onprem
DR   : neuroplan-login-mvp/k8s/dr
```

### Harbor Registry

| Role | 역할 |
|---|---|
| [`harbor`](harbor/) | Harbor Offline Installer 배포, `harbor.yml` 생성, Docker Compose 기반 서비스 구성 및 Registry API 검증 |
| [`harbor_bootstrap`](harbor_bootstrap/) | Harbor Project, 사용자, Jenkins/Kubernetes Robot Account 및 권한을 API로 초기 구성 |
| [`harbor_registry`](harbor_registry/) | Main Worker의 containerd와 DR k3s가 Harbor Registry를 사용할 수 있도록 Registry 설정 배포 |
| [`harbor_pull_secret`](harbor_pull_secret/) | 애플리케이션 Namespace에 Harbor 인증용 `kubernetes.io/dockerconfigjson` Image Pull Secret 생성 |

프로젝트에서 사용하는 Private Registry는 다음 주소를 기준으로 구성합니다.

```text
harbor.nplan.local:80
```

### Kubernetes 및 DR

| Role | 역할 |
|---|---|
| [`k8s_common`](k8s_common/) | Control Plane/Worker 노드의 SELinux Permissive 및 firewalld 비활성화 등 Kubernetes 공통 사전 설정 |
| [`k3s`](k3s/) | 고정 버전의 DR k3s 설치, 설정 파일 배포, 서비스 기동, API 및 Node Ready 상태 검증 |

### 모니터링

| Role | 역할 |
|---|---|
| [`node_exporter_vm`](node_exporter_vm/) | 일반 VM에 Node Exporter를 설치해 시스템 메트릭 수집 대상 구성 |
| [`node_exporter_k8s`](node_exporter_k8s/) | Main Kubernetes에 Node Exporter DaemonSet을 배포해 노드 메트릭 수집 |
| [`k3s_monitoring`](k3s_monitoring/) | DR k3s에 Node Exporter, kube-state-metrics 및 외부 Prometheus 접근용 RBAC 구성 |

### Object Storage

| Role | 역할 |
|---|---|
| [`minio`](minio/) | 별도 데이터 마운트를 검증한 뒤 MinIO Community를 설치하고 systemd 서비스, S3 API, Console 및 Health Endpoint 검증 |

## 전체 Role 구조

```text
roles/
├── argocd/
├── argocd_dr_application/
├── argocd_dr_cluster/
├── common/
├── dns_client/
├── docker/
├── dr_application_secrets/
├── harbor/
├── harbor_bootstrap/
├── harbor_pull_secret/
├── harbor_registry/
├── helm/
├── hosts_config/
├── infra_check/
├── java/
├── jenkins/
├── k3s/
├── k3s_argocd_access/
├── k3s_monitoring/
├── k8s_common/
├── kubectl_client/
├── maven/
├── minio/
├── node_exporter_k8s/
├── node_exporter_vm/
├── nodejs/
└── ntp/
```

## 실행 전 준비

저장소 루트에서 Ansible을 실행합니다.

- Inventory 및 대상 그룹이 올바르게 구성되어 있어야 합니다.
- 원격 접속 계정과 SSH 인증이 준비되어 있어야 합니다.
- Vault 변수가 필요한 Role은 `--ask-vault-pass` 옵션으로 실행합니다.
- Kubernetes API를 사용하는 Role은 사용 가능한 kubeconfig와 적절한 권한이 필요합니다.
- Harbor 및 MinIO처럼 별도 데이터 디스크를 사용하는 Role은 필요한 마운트가 사전에 완료되어 있어야 합니다.
- Check Mode 지원 범위는 Role마다 다르므로 실제 배포 전 각 Role의 구현을 확인합니다.

## 실행 방법

### 공통 설정

```bash
ansible-playbook -i inventory/hosts.ini playbooks/common.yml --ask-vault-pass
```

### DevOps 환경 구성

Java, Maven, Node.js, Docker, Jenkins, kubectl, Helm, Argo CD 등 DevOps 환경을 구성합니다.

```bash
ansible-playbook -i inventory/hosts.ini playbooks/devops.yml --ask-vault-pass
```

### CI/CD 전체 구성

Jenkins, Harbor, Argo CD 및 DR GitOps 연동을 포함한 CI/CD 구성을 순서대로 적용할 때 사용합니다.

```bash
ansible-playbook -i inventory/hosts.ini playbooks/cicd.yml --ask-vault-pass
```

### Harbor 구성

```bash
ansible-playbook -i inventory/hosts.ini playbooks/harbor.yml --ask-vault-pass
ansible-playbook -i inventory/hosts.ini playbooks/harbor-bootstrap.yml --ask-vault-pass
ansible-playbook -i inventory/hosts.ini playbooks/harbor-registry.yml --ask-vault-pass
ansible-playbook -i inventory/hosts.ini playbooks/harbor-pull-secret.yml --ask-vault-pass
```

### DR Argo CD 연동

```bash
ansible-playbook -i inventory/hosts.ini playbooks/k3s-argocd-access.yml --ask-vault-pass
ansible-playbook -i inventory/hosts.ini playbooks/argocd-dr.yml --ask-vault-pass
ansible-playbook -i inventory/hosts.ini playbooks/argocd-dr-application.yml --ask-vault-pass
ansible-playbook -i inventory/hosts.ini playbooks/dr-application-secrets.yml --ask-vault-pass
```

## Role 단독 실행 예시

특정 Role만 검증하거나 재적용할 경우 임시 Playbook에서 필요한 Role만 지정할 수 있습니다.

```yaml
---
- name: Apply selected role
  hosts: target_group
  become: true

  roles:
    - role: common
```

Role에 따라 `become`, kubeconfig, Vault 변수, 대상 Inventory Group 등의 요구사항이 다르므로 해당 Role의 `defaults/`, `vars/`, `templates/`, `tasks/`를 함께 확인합니다.

## Check Mode 및 검증

변경 예정 항목을 확인할 수 있는 Role은 다음과 같이 Check Mode로 실행할 수 있습니다.

```bash
ansible-playbook -i inventory/hosts.ini playbooks/devops.yml --check --ask-vault-pass
```

다만 일부 상태 조회와 검증 명령은 Check Mode에서도 실행될 수 있으며, 외부 서비스 API나 Kubernetes 상태에 따라 결과가 달라질 수 있습니다.

일반 실행 후에는 다음 항목을 중심으로 검증합니다.

```bash
# Ansible 실행 결과
ansible-playbook -i inventory/hosts.ini playbooks/common.yml --ask-vault-pass

# Main Kubernetes
kubectl get nodes
kubectl get pods -A

# Argo CD
kubectl get pods -n argocd
kubectl get applications -n argocd

# Harbor
curl -I http://harbor.nplan.local:80/v2/
```

최종적으로 Ansible `PLAY RECAP`의 `failed=0` 여부와 각 서비스의 실제 상태를 함께 확인합니다.

## Role 디렉터리 기본 형태

Role마다 필요한 파일만 사용하며 일반적인 구조는 다음과 같습니다.

```text
role_name/
├── defaults/
│   └── main.yml
├── handlers/
│   └── main.yml
├── tasks/
│   └── main.yml
├── templates/
│   └── ...
└── README.md
```

- `tasks/main.yml`: Role의 주요 작업
- `defaults/main.yml`: 사용자가 덮어쓸 수 있는 기본 변수
- `handlers/main.yml`: 서비스 재시작 등 변경 후 처리
- `templates/`: Jinja2 기반 설정 파일 또는 Manifest
- `README.md`: Role 목적, 변수, 실행 방법, 검증 범위 문서화

모든 Role이 위 디렉터리를 전부 포함하는 것은 아닙니다.

## 관련 코드

- [저장소 전체 README](../README.md)
- [Playbooks](../playbooks/)
- [Inventory](../inventory/)
- [Argo CD Role](argocd/)
- [Jenkins Role](jenkins/)
- [Harbor Role](harbor/)
- [DR k3s Role](k3s/)
- [MinIO Role](minio/)
- [애플리케이션 저장소](https://github.com/Infrastructure-hybrid09/onprem-k8s-application-devopsVM)
