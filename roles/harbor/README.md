# Harbor Private Registry 설치 및 구성

InfraReady 온프레미스 환경에 **Harbor Private Container Registry**를 설치하고 기본 Registry 서비스를 구성하는 Ansible Role입니다.

이 Role은 Harbor 서버 자체의 설치, 설정 파일 배포, 컨테이너 기동 및 Registry API 검증까지 담당합니다.

프로젝트 생성, Robot Account 구성, Kubernetes/DR k3s의 Registry 연동 및 Image Pull Secret 구성은 별도 Role에서 관리합니다.

---

## 주요 기능

- Harbor 데이터 볼륨 마운트 여부 사전 검증
- Docker 및 Docker Compose 설치 상태 확인
- Harbor Offline Installer 다운로드 및 압축 해제
- Jinja2 템플릿 기반 `harbor.yml` 설정 배포
- 최초 설치 시 Harbor Installer 실행
- 설정 변경 시 `prepare` 실행 후 Harbor 재기동
- Docker Compose 기반 Harbor 컨테이너 실행 상태 유지
- Harbor Registry API `/v2/` 응답 검증
- 관리자 비밀번호 및 DB 비밀번호를 Ansible Vault 변수로 주입
- HTTP 기반 Private Registry 구성
- 필요 시 HTTPS 설정을 활성화할 수 있도록 변수 제공

---

## Role 구성

```text
roles/harbor/
├── defaults/
│   └── main.yml
├── handlers/
│   └── main.yml
├── tasks/
│   └── main.yml
└── templates/
    └── harbor.yml.j2
```

| 파일 | 역할 |
|---|---|
| [defaults/main.yml](defaults/main.yml) | Harbor 버전, Hostname, Port, 설치 경로, 데이터 경로 등 기본값 정의 |
| [tasks/main.yml](tasks/main.yml) | 설치 전 검증, Offline Installer 설치, 설정 적용, 서비스 기동 및 Registry API 검증 |
| [handlers/main.yml](handlers/main.yml) | 설정 변경 시 Docker Compose를 이용한 Harbor 재기동 |
| [templates/harbor.yml.j2](templates/harbor.yml.j2) | 실제 Harbor 설정 파일을 생성하는 Jinja2 템플릿 |

---

## 기본 구성

현재 프로젝트의 기본 Harbor 구성은 다음과 같습니다.

| 항목 | 설정 |
|---|---|
| Harbor Version | `v2.15.2` |
| Hostname | `harbor.nplan.local` |
| HTTP Port | `80` |
| HTTPS | 비활성화 |
| 설치 루트 | `/opt/harbor-install` |
| Harbor 설치 디렉터리 | `/opt/harbor-install/harbor` |
| 데이터 볼륨 | `/data` |
| 배포 방식 | Harbor Offline Installer + Docker Compose |

Registry 주소:

```text
harbor.nplan.local:80
```

---

## 실행 전 준비

이 Role을 실행하기 전에 다음 조건이 준비되어 있어야 합니다.

### 1. Harbor 데이터 볼륨

기본 데이터 경로인 `/data`가 별도 파일시스템 또는 디스크에 **마운트된 상태**여야 합니다.

Role은 다음과 같이 마운트 여부를 확인하며, 마운트되어 있지 않으면 Harbor 설치를 중단합니다.

```bash
mountpoint -q /data
```

> 이 Role은 `/data` 디스크의 파티셔닝, 포맷 또는 마운트 작업을 수행하지 않습니다.

### 2. Docker / Docker Compose

Harbor는 Docker Compose 기반으로 실행되므로 대상 서버에서 다음 명령이 정상 동작해야 합니다.

```bash
docker --version
docker compose version
```

프로젝트의 [harbor.yml](../../playbooks/harbor.yml) Playbook에서는 `docker` Role을 먼저 실행한 뒤 `harbor` Role을 실행합니다.

### 3. DNS

대상 서버 및 Harbor를 사용하는 클라이언트에서 다음 이름이 Harbor 서버로 정상 해석되어야 합니다.

```text
harbor.nplan.local
```

### 4. Vault 변수

Harbor 설정 템플릿에서 다음 민감정보를 사용합니다.

```yaml
vault_harbor_admin_password
vault_harbor_db_password
```

이 값들은 Role 기본값에 포함되지 않으며 Ansible Vault 등 외부 변수에서 제공해야 합니다.

---

## 주요 변수

### 기본값

| 변수 | 기본값 | 설명 |
|---|---|---|
| `harbor_version` | `v2.15.2` | 설치할 Harbor 버전 |
| `harbor_hostname` | `harbor.nplan.local` | Harbor Registry Hostname |
| `harbor_http_port` | `80` | HTTP 서비스 포트 |
| `harbor_install_root` | `/opt/harbor-install` | Harbor Installer 저장 및 설치 루트 |
| `harbor_install_dir` | `{{ harbor_install_root }}/harbor` | Harbor 실제 설치 디렉터리 |
| `harbor_installer_archive` | `{{ harbor_install_root }}/harbor-offline-installer-{{ harbor_version }}.tgz` | Offline Installer 압축 파일 |
| `harbor_installer_url` | Harbor GitHub Release URL | Offline Installer 다운로드 주소 |
| `harbor_data_volume` | `/data` | Harbor 영구 데이터 저장 경로 |
| `harbor_https_enabled` | `false` | HTTPS 활성화 여부 |
| `harbor_https_port` | `443` | HTTPS 포트 |
| `harbor_https_certificate` | `/etc/harbor/certs/harbor.crt` | TLS 인증서 경로 |
| `harbor_https_private_key` | `/etc/harbor/certs/harbor.key` | TLS Private Key 경로 |

### 외부에서 제공해야 하는 변수

| 변수 | 용도 |
|---|---|
| `vault_harbor_admin_password` | Harbor 초기 `admin` 계정 비밀번호 |
| `vault_harbor_db_password` | Harbor 내부 PostgreSQL DB 비밀번호 |

민감정보는 일반 변수 파일에 평문으로 저장하지 않고 Ansible Vault로 관리합니다.

---

## 설치 흐름

Role의 주요 실행 흐름은 다음과 같습니다.

```text
/data Mount Check
        │
        ▼
Docker / Docker Compose Check
        │
        ▼
Harbor Install Directory 생성
        │
        ▼
Harbor Installer 존재 여부 확인
        │
        ├── 없음 ──> Offline Installer Download / Extract
        │
        ▼
harbor.yml Template 배포
        │
        ▼
기존 docker-compose.yml 확인
        │
        ├── 최초 설치
        │      └── install.sh 실행
        │
        └── 기존 설치 + 설정 변경
               └── prepare 실행
                       │
                       ▼
                Harbor Restart
        │
        ▼
docker compose up -d
        │
        ▼
docker compose ps
        │
        ▼
Registry API /v2/ 검증
```

---

## 최초 설치와 재실행

### 최초 설치

`{{ harbor_install_dir }}/docker-compose.yml`이 존재하지 않으면 최초 설치로 판단하여 다음 스크립트를 실행합니다.

```bash
./install.sh
```

### 기존 설치

기존 `docker-compose.yml`이 존재하고 `harbor.yml` 설정이 변경된 경우 다음 순서로 설정을 다시 적용합니다.

```text
harbor.yml 변경
      │
      ▼
./prepare
      │
      ▼
Restart Harbor Handler
      │
      ▼
docker compose up -d
```

설정 변경이 없는 경우 불필요한 `prepare` 실행을 하지 않도록 구성되어 있습니다.

---

## Harbor 설정 템플릿

[templates/harbor.yml.j2](templates/harbor.yml.j2)는 Harbor 공식 설정 구조를 기반으로 프로젝트 값을 주입합니다.

주요 적용 항목:

```yaml
hostname: harbor.nplan.local

http:
  port: 80

harbor_admin_password: <Vault Variable>

database:
  password: <Vault Variable>

data_volume: /data
```

현재 프로젝트에서는 다음 설정을 사용합니다.

```yaml
harbor_https_enabled: false
```

따라서 Harbor Registry는 다음 주소를 사용합니다.

```text
http://harbor.nplan.local:80
```

HTTPS를 활성화할 경우 `harbor_https_enabled`를 `true`로 변경하고 인증서 및 Private Key 경로를 설정해야 합니다.

---

## 실행 방법

저장소 루트에서 실행합니다.

### Harbor 서버 구성

```bash
ansible-playbook \
  -i inventory/hosts.ini \
  playbooks/harbor.yml \
  -K \
  --ask-vault-pass
```

[playbooks/harbor.yml](../../playbooks/harbor.yml)은 `minio_nodes`를 대상으로 다음 Role을 순서대로 실행합니다.

```text
docker
  │
  ▼
harbor
```

즉 Harbor Role 단독 실행 전에 필요한 Docker 환경을 함께 구성할 수 있습니다.

---

## 전체 CI/CD 구성에서의 실행 순서

Harbor는 전체 CI/CD 자동화의 앞단에서 Private Registry 역할을 담당합니다.

[playbooks/cicd.yml](../../playbooks/cicd.yml)의 관련 실행 순서는 다음과 같습니다.

```text
1. harbor.yml
   └── Harbor Registry Server 설치

2. harbor-bootstrap.yml
   └── Harbor Project / Robot Account / Administrator 구성

3. devops.yml
   └── Jenkins / Docker / Helm / Argo CD 구성

4. harbor-registry.yml
   └── Main Kubernetes Worker / DR k3s Registry 설정

5. harbor-pull-secret.yml
   └── Main Kubernetes / DR k3s Image Pull Secret 구성
```

---

## 검증

Role 실행 후 Harbor 컨테이너 상태를 확인할 수 있습니다.

```bash
cd /opt/harbor-install/harbor
docker compose ps
```

Registry API 확인:

```bash
curl -I http://harbor.nplan.local:80/v2/
```

Role 자체에서는 다음 API를 최대 10회 재시도하여 확인합니다.

```text
http://harbor.nplan.local:80/v2/
```

정상 상태에서는 HTTP `200` 또는 인증이 필요한 Registry의 경우 `401` 응답을 정상으로 처리합니다.

---

## Check Mode 주의사항

이 Role의 일부 사전 검증 및 Registry API 확인 작업은 실제 상태 조회를 위해 Check Mode에서도 실행되도록 구성되어 있습니다.

다만 Harbor 최초 설치, Installer 다운로드, 설정 변경 및 컨테이너 재기동과 같은 실제 변경 작업은 일반 실행에서 확인하는 것이 적절합니다.

특히 `/data` 마운트, Docker/Docker Compose 준비 상태와 Vault 변수는 Check Mode에서도 사전에 준비되어 있어야 합니다.

---

## Role 범위

이 `harbor` Role의 담당 범위는 **Harbor Registry 서버 자체 설치 및 서비스 검증**입니다.

다음 기능은 별도 Role에서 관리합니다.

| Role | 역할 |
|---|---|
| [harbor_bootstrap](../harbor_bootstrap) | Harbor Project, Robot Account 및 관리자 구성 |
| [harbor_registry](../harbor_registry) | Main Kubernetes Worker와 DR k3s의 Harbor Registry Runtime 설정 |
| [harbor_pull_secret](../harbor_pull_secret) | Kubernetes 및 DR 환경의 Image Pull Secret 생성 |
| [docker](../docker) | Harbor 실행에 필요한 Docker Engine 및 관련 환경 구성 |

---

## CI/CD에서의 Harbor 역할

NeuroPlan CI/CD에서 Harbor는 Jenkins와 Kubernetes 사이의 Private Container Registry로 사용됩니다.

```text
Jenkins
   │
   │ Docker Build
   ▼
Harbor Registry
harbor.nplan.local:80
   │
   │ Image Pull
   ▼
Main Kubernetes / DR k3s
```

애플리케이션 이미지는 Harbor의 `neuroplan` 프로젝트를 통해 관리되며, 프로젝트 및 Robot Account 구성은 `harbor_bootstrap` Role에서 별도로 수행합니다.

---

## 관련 코드

- [Harbor 실행 Playbook](../../playbooks/harbor.yml)
- [전체 CI/CD 구성 Playbook](../../playbooks/cicd.yml)
- [Harbor Bootstrap Role](../harbor_bootstrap)
- [Harbor Registry Runtime Role](../harbor_registry)
- [Harbor Pull Secret Role](../harbor_pull_secret)
- [Docker Role](../docker)
- [Jenkins Role](../jenkins)

---

## 최종 구성

```text
Harbor Server
harbor.nplan.local:80
        │
        ├── Offline Installer
        ├── Docker Compose
        ├── Persistent Data: /data
        └── Registry API: /v2/
                │
                ▼
        Harbor Bootstrap
        Project / Robot Accounts
                │
                ▼
      Jenkins Image Push
                │
                ▼
    Main Kubernetes / DR k3s
         Image Pull
```
