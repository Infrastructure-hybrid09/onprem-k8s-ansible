# DR k3s 설치 및 기본 구성

InfraReady 온프레미스 환경의 **DR(Disaster Recovery) 단일 k3s 클러스터**를 설치하고 기본 네트워크 및 노드 설정을 구성하는 Ansible Role입니다.

이 Role은 DR 서버가 사전에 정의한 대상 서버인지 확인한 뒤 고정 버전의 k3s를 설치하고, 설정 파일을 배포하며, 서비스 기동 후 Kubernetes API와 Node 상태까지 검증합니다.

Argo CD가 DR k3s에 접근하기 위한 ServiceAccount/RBAC 구성은 별도 `k3s_argocd_access` Role에서 담당합니다.

---

## 주요 기능

- 필수 k3s 변수 설정 여부 검증
- Management IP 및 Node IP가 실제 대상 서버에 존재하는지 안전성 검증
- `curl`, `container-selinux` 사전 패키지 설치
- k3s 설정 디렉터리 및 Installer 경로 생성
- 기존 k3s 설치 여부 확인
- 기존 설치 버전과 설정 버전 비교
- 의도하지 않은 자동 Upgrade/Downgrade 차단
- Jinja2 기반 `/etc/rancher/k3s/config.yaml` 배포
- 공식 설치 스크립트를 이용한 고정 버전 k3s 설치
- k3s systemd 서비스 활성화 및 기동
- 설정 변경 시 k3s 재시작
- Kubernetes API `6443` 포트 기동 대기
- k3s Node `Ready=True` 검증
- Node `InternalIP`이 설정값과 일치하는지 검증
- Traefik 및 ServiceLB 기본 컴포넌트 비활성화
- Supervisor Metrics 활성화

---

## Role 구성

```text
roles/k3s/
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
│   └── config.yaml.j2
└── vars/
    └── main.yml
```

| 파일 | 역할 |
|---|---|
| [defaults/main.yml](defaults/main.yml) | k3s 버전, 네트워크, NodePort 범위, Cluster Domain 등 기본값 정의 |
| [tasks/main.yml](tasks/main.yml) | 대상 서버 검증, k3s 설치, 서비스 기동 및 상태 검증 |
| [handlers/main.yml](handlers/main.yml) | 설정 변경 시 k3s 서비스 재시작 |
| [templates/config.yaml.j2](templates/config.yaml.j2) | `/etc/rancher/k3s/config.yaml` 생성용 Jinja2 템플릿 |
| [meta/main.yml](meta/main.yml) | Ansible Role 메타데이터 |
| [vars/main.yml](vars/main.yml) | 현재 별도 Role 변수 없음 |

---

## 현재 프로젝트 구성

현재 `inventory/group_vars/k3s_nodes.yml`에서 사용하는 DR k3s 설정은 다음과 같습니다.

| 항목 | 설정 |
|---|---|
| k3s Version | `v1.35.4+k3s1` |
| Node Name | `dr-k3s` |
| Management IP | `192.168.14.71` |
| Node / Internal IP | `192.168.34.71` |
| Advertise Address | `192.168.34.71` |
| Flannel Interface | `ens192` |
| Pod CIDR | `10.42.0.0/16` |
| Service CIDR | `10.43.0.0/16` |
| NodePort Range | `30000-32767` |
| Cluster Domain | `neuroplan.local` |
| Disabled Components | `traefik`, `servicelb` |

DR k3s Node:

```text
dr-k3s

Management : 192.168.14.71
Internal   : 192.168.34.71
```

---

## 실행 전 준비

Role 실행 전에 다음 조건을 확인합니다.

### 1. Inventory

대상 서버가 `k3s_nodes` 그룹에 포함되어 있어야 합니다.

Playbook에서는 다음 그룹을 대상으로 실행합니다.

```yaml
hosts: k3s_nodes
```

### 2. 대상 서버 IP

다음 변수는 반드시 설정되어 있어야 합니다.

```yaml
k3s_management_ip
k3s_node_ip
k3s_node_name
```

현재 프로젝트 값:

```yaml
k3s_management_ip: "192.168.14.71"
k3s_node_ip: "192.168.34.71"
k3s_node_name: "dr-k3s"
```

Role은 Ansible Facts의 `ansible_all_ipv4_addresses`를 확인하여 Management IP와 Node IP가 실제 대상 서버에 존재하는지 검증합니다.

예상 IP와 실제 서버 IP가 다르면 설치를 중단합니다.

### 3. 외부 Installer 접근

신규 설치 시 다음 URL에서 k3s Installer를 다운로드합니다.

```text
https://get.k3s.io
```

Installer 저장 경로:

```text
/usr/local/src/install-k3s.sh
```

### 4. 권한

패키지 설치, `/etc/rancher/k3s` 설정 및 systemd 서비스를 관리하므로 `become: true` 권한이 필요합니다.

---

## 주요 변수

### 기본값

| 변수 | 기본값 | 설명 |
|---|---|---|
| `k3s_version` | `v1.35.4+k3s1` | 설치할 고정 k3s 버전 |
| `k3s_install_url` | `https://get.k3s.io` | 공식 k3s Installer URL |
| `k3s_install_script_path` | `/usr/local/src/install-k3s.sh` | Installer 저장 경로 |
| `k3s_node_name` | `dr-k3s` | Kubernetes Node 이름 |
| `k3s_management_ip` | `""` | Management Network IP |
| `k3s_node_ip` | `""` | Kubernetes Node/Internal IP |
| `k3s_advertise_address` | `""` | Kubernetes API Advertise Address |
| `k3s_flannel_iface` | `ens192` | Flannel이 사용할 네트워크 인터페이스 |
| `k3s_cluster_cidr` | `10.42.0.0/16` | Pod Network CIDR |
| `k3s_service_cidr` | `10.43.0.0/16` | Service Network CIDR |
| `k3s_service_node_port_range` | `30000-32767` | NodePort 사용 범위 |
| `k3s_cluster_domain` | `neuroplan.local` | Kubernetes Cluster Domain |
| `k3s_disable_components` | `traefik`, `servicelb` | 비활성화할 기본 k3s 컴포넌트 |
| `k3s_supervisor_metrics` | `true` | Supervisor Metrics 활성화 여부 |

---

## Inventory Group Variables

현재 프로젝트에서는 Role 기본값 중 환경 종속적인 값을 `inventory/group_vars/k3s_nodes.yml`에서 덮어씁니다.

```yaml
---
k3s_version: "v1.35.4+k3s1"

k3s_node_name: "dr-k3s"

k3s_management_ip: "192.168.14.71"
k3s_node_ip: "192.168.34.71"
k3s_advertise_address: "192.168.34.71"

k3s_flannel_iface: "ens192"

k3s_cluster_cidr: "10.42.0.0/16"
k3s_service_cidr: "10.43.0.0/16"
k3s_service_node_port_range: "30000-32767"

k3s_cluster_domain: "neuroplan.local"

k3s_disable_components:
  - traefik
  - servicelb
```

---

## k3s 설정 파일

Role은 [templates/config.yaml.j2](templates/config.yaml.j2)를 이용해 다음 파일을 생성합니다.

```text
/etc/rancher/k3s/config.yaml
```

프로젝트 설정을 적용하면 핵심 값은 다음과 같은 형태가 됩니다.

```yaml
node-name: dr-k3s

node-ip: 192.168.34.71
advertise-address: 192.168.34.71

tls-san:
  - 192.168.34.71
  - dr-k3s

flannel-iface: ens192

cluster-cidr: 10.42.0.0/16
service-cidr: 10.43.0.0/16
service-node-port-range: 30000-32767

cluster-domain: neuroplan.local

selinux: true
supervisor-metrics: true

disable:
  - traefik
  - servicelb
```

설정 파일 권한은 다음과 같이 적용합니다.

```text
root:root
0600
```

---

## 기본 컴포넌트 비활성화

현재 프로젝트에서는 k3s 기본 제공 컴포넌트 중 다음 두 항목을 비활성화합니다.

```text
traefik
servicelb
```

설정:

```yaml
k3s_disable_components:
  - traefik
  - servicelb
```

해당 값은 `config.yaml.j2`의 `disable` 항목으로 반영됩니다.

---

## 설치 흐름

Role의 전체 실행 흐름은 다음과 같습니다.

```text
필수 변수 검증
      │
      ▼
Management / Node IP 안전성 검증
      │
      ▼
curl / container-selinux 설치
      │
      ▼
/etc/rancher/k3s 생성
/usr/local/src 생성
      │
      ▼
기존 k3s Binary 확인
      │
      ├── 설치됨
      │      │
      │      ▼
      │   현재 Version 확인
      │      │
      │      ▼
      │   설정 Version과 비교
      │      │
      │      └── 불일치 → 실행 중단
      │
      └── 미설치
             │
             ▼
        Installer Download
             │
             ▼
        지정 Version 설치
      │
      ▼
config.yaml 배포
      │
      ▼
k3s Service Enable / Start
      │
      ▼
설정 변경 Handler 적용
      │
      ▼
API 6443 대기
      │
      ▼
Node Ready=True 검증
      │
      ▼
InternalIP 검증
```

---

## 버전 고정 정책

이 Role은 k3s 버전을 명시적으로 고정합니다.

```yaml
k3s_version: "v1.35.4+k3s1"
```

기존에 `/usr/local/bin/k3s`가 존재하면 다음 명령으로 현재 버전을 확인합니다.

```bash
/usr/local/bin/k3s --version
```

설치된 버전에 `k3s_version` 값이 포함되어 있지 않으면 Role은 실행을 중단합니다.

즉 이 Role은 기존 k3s에 대해 자동 Upgrade 또는 Downgrade를 수행하지 않습니다.

```text
설정 버전 == 설치 버전
        │
        ├── Yes → 계속 실행
        │
        └── No  → 실행 중단
```

버전 변경이 필요한 경우 별도의 업그레이드 절차를 통해 관리해야 합니다.

---

## 설정 변경 처리

`/etc/rancher/k3s/config.yaml` 내용이 변경되면 다음 Handler가 호출됩니다.

```yaml
- name: Restart k3s
  ansible.builtin.systemd:
    name: k3s
    state: restarted
```

Task에서는 서비스 기동 후 pending Handler를 즉시 적용합니다.

```text
config.yaml 변경
      │
      ▼
Restart k3s Handler
      │
      ▼
API / Node 상태 검증
```

---

## 실행 방법

저장소 루트에서 실행합니다.

```bash
ansible-playbook \
  -i inventory/hosts.ini \
  playbooks/k3s.yml \
  -K
```

프로젝트의 [playbooks/k3s.yml](../../playbooks/k3s.yml)은 `k3s_nodes`를 대상으로 다음 Role을 순서대로 실행합니다.

```text
k3s
 │
 ▼
k3s_argocd_access
```

따라서 `k3s.yml` Playbook을 실행하면 k3s 설치 후 Argo CD 접근을 위한 별도 구성까지 이어서 적용됩니다.

---

## 설치 후 검증

Role 자체에서 다음 항목을 자동으로 검증합니다.

### 1. Kubernetes API

설정된 `k3s_node_ip`의 TCP `6443` 포트가 시작될 때까지 최대 60초 대기합니다.

현재 프로젝트:

```text
192.168.34.71:6443
```

수동 확인 예:

```bash
ss -lnt | grep 6443
```

### 2. Node Ready

다음과 같은 방식으로 `Ready` Condition을 조회합니다.

```bash
/usr/local/bin/k3s kubectl get node dr-k3s \
  -o 'jsonpath={.status.conditions[?(@.type=="Ready")].status}'
```

기대 결과:

```text
True
```

### 3. Internal IP

다음과 같은 방식으로 Node InternalIP를 조회합니다.

```bash
/usr/local/bin/k3s kubectl get node dr-k3s \
  -o 'jsonpath={.status.addresses[?(@.type=="InternalIP")].address}'
```

현재 프로젝트의 기대값:

```text
192.168.34.71
```

설정값과 실제 InternalIP가 다르면 Role 실행을 실패 처리합니다.

---

## 추가 수동 검증

Role 실행 후 다음 명령으로 DR k3s 상태를 확인할 수 있습니다.

```bash
systemctl status k3s

/usr/local/bin/k3s kubectl get nodes -o wide

/usr/local/bin/k3s kubectl get pods -A

/usr/local/bin/k3s kubectl get svc -A
```

예상 Node:

```text
dr-k3s
```

---

## Argo CD 연동과 Role 범위

이 `k3s` Role의 담당 범위는 **DR k3s 설치 및 기본 클러스터 검증**입니다.

Argo CD 관련 구성은 별도 Role에서 처리합니다.

| Role | 역할 |
|---|---|
| [k3s](../k3s) | DR k3s 설치, 네트워크 설정, 서비스 및 Node 상태 검증 |
| [k3s_argocd_access](../k3s_argocd_access) | DR k3s에 Argo CD ServiceAccount/RBAC 및 접근 리소스 구성 |
| [argocd_dr_cluster](../argocd_dr_cluster) | DR k3s 클러스터를 Main Argo CD 관리 대상으로 등록 |
| [argocd_dr_application](../argocd_dr_application) | DR용 Argo CD Application 구성 |

`k3s_argocd_access`는 k3s 서비스가 `active`인지 먼저 확인한 후 Argo CD 접근 Manifest를 적용하고, 애플리케이션 Namespace 권한을 검증합니다.

---

## DR GitOps 흐름

k3s 설치 후 DR GitOps 연결은 다음 구조로 이어집니다.

```text
DR Server
192.168.14.71 / 192.168.34.71
        │
        ▼
      k3s
v1.35.4+k3s1
        │
        ▼
k3s_argocd_access
ServiceAccount / RBAC
        │
        ▼
argocd_dr_cluster
Main Argo CD에 DR 등록
        │
        ▼
argocd_dr_application
DR Application 구성
        │
        ▼
     DR k3s
```

---

## Check Mode 및 주의사항

이 Role은 실제 서버 상태와 설치 상태를 적극적으로 검증합니다.

특히 다음 작업은 대상 서버의 실제 상태에 의존합니다.

- `ansible_all_ipv4_addresses` 기반 IP 검증
- 기존 `/usr/local/bin/k3s` 존재 여부
- 설치된 k3s 버전 조회
- Kubernetes API `6443` 상태
- Node `Ready` 상태
- Node `InternalIP`

따라서 신규 설치 전체 과정을 검증할 때는 일반 실행 결과를 기준으로 확인하는 것이 적절합니다.

---

## 안전성 검증

이 Role에는 잘못된 서버에 DR k3s가 설치되는 것을 방지하기 위한 검증이 포함되어 있습니다.

### 필수 변수 확인

다음 값이 비어 있으면 실행을 중단합니다.

```text
k3s_management_ip
k3s_node_ip
k3s_node_name
```

### 대상 서버 IP 확인

설정된 두 IP가 실제 대상 서버의 IPv4 주소 목록에 모두 존재해야 합니다.

```text
Management IP : 192.168.14.71
Node IP       : 192.168.34.71
```

### 버전 보호

기존 k3s가 설치되어 있는 경우 설정 버전과 실제 설치 버전이 다르면 자동 변경하지 않고 실행을 중단합니다.

이 세 검증을 통해 대상 서버와 버전이 예상 상태인지 확인한 뒤에만 구성을 계속합니다.

---

## 관련 코드

- [k3s 실행 Playbook](../../playbooks/k3s.yml)
- [k3s Group Variables](../../inventory/group_vars/k3s_nodes.yml)
- [k3s Argo CD Access Role](../k3s_argocd_access)
- [DR Argo CD Cluster Role](../argocd_dr_cluster)
- [DR Argo CD Application Role](../argocd_dr_application)
- [Harbor Registry Role](../harbor_registry)
- [DR Monitoring Role](../k3s_monitoring)

---

## 최종 구성

```text
DR k3s Node
dr-k3s
192.168.14.71 / 192.168.34.71
        │
        ├── k3s v1.35.4+k3s1
        ├── Pod CIDR     10.42.0.0/16
        ├── Service CIDR 10.43.0.0/16
        ├── NodePort     30000-32767
        ├── Flannel      ens192
        ├── Traefik      Disabled
        ├── ServiceLB    Disabled
        └── API          192.168.34.71:6443
                │
                ▼
          Node Ready=True
                │
                ▼
       InternalIP Verification
                │
                ▼
       Argo CD Access Setup
```
