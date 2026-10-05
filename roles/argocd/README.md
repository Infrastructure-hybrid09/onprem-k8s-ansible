# Argo CD 설치 및 Application 구성

InfraReady 온프레미스 Kubernetes 환경에 Argo CD를 설치하고, NeuroPlan 애플리케이션의 배포 설정을 연결하는 Ansible Role입니다.

애플리케이션 소스와 Kubernetes 배포 설정은 **동일한 Git 저장소**에서 관리합니다. Jenkins가 이미지 빌드·Harbor Push·Kustomize 이미지 태그 갱신을 수행하며, Argo CD는 지정된 배포 경로를 기준으로 클러스터에 변경사항을 반영합니다.

## 주요 기능

- Helm 실행 파일 및 기존 Argo CD Release 조회
- Release 설치 여부와 Chart 버전을 비교해 필요한 경우 설치·업그레이드
- Argo CD Deployment 및 Application Controller StatefulSet의 rollout 상태 확인
- Jinja2 템플릿으로 Application Manifest 생성 및 적용
- Application 상태와 설치 점검 결과 출력
- Check Mode에서 설치 필요 여부 확인 및 Application 템플릿 렌더링 검증

## 구성 파일

| 파일 | 역할 |
|---|---|
| [tasks/main.yml](tasks/main.yml) | 설치 여부 확인, Helm 설치·업그레이드, Application 적용 및 상태 조회 |
| [defaults/main.yml](defaults/main.yml) | Namespace, Chart 버전, kubeconfig 등 기본값 |
| [templates/application.yaml.j2](templates/application.yaml.j2) | Git 저장소·배포 경로·동기화 정책을 설정하는 Application 템플릿 |

## 실행 전 준비

Role은 DevOps 호스트에서 Helm과 kubectl을 실행해 Kubernetes를 관리합니다.

- 대상 호스트에 `/usr/local/bin/helm`과 PATH에서 실행 가능한 `kubectl`이 필요합니다.
- 실행 계정이 `argocd_kubeconfig`를 읽을 수 있고, 설치 및 Application 관리 권한을 가져야 합니다.
- Helm 저장소와 필요한 이미지 저장소에 접근할 수 있어야 합니다.
- Argo CD가 애플리케이션 Git 저장소에 접근할 수 있어야 합니다. 비공개 저장소 인증은 별도로 준비합니다.
- 배포 대상 Namespace와 애플리케이션에 필요한 Secret 등은 사전에 준비합니다.

[devops.yml](../../playbooks/devops.yml)은 `kubectl_client`, `helm` Role을 이 Role보다 먼저 실행합니다. Role 자체의 메타데이터에는 자동 실행되는 의존 Role이 없습니다.

## 주요 변수

### 기본값

| 변수 | 기본값 | 설명 |
|---|---|---|
| `argocd_namespace` | `argocd` | Argo CD 설치 Namespace |
| `argocd_release_name` | `argocd` | Helm Release 이름 |
| `argocd_repo_name` | `argo` | Helm 저장소 별칭 |
| `argocd_repo_url` | `https://argoproj.github.io/argo-helm` | Helm 저장소 주소 |
| `argocd_chart` | `argo/argo-cd` | 설치할 Chart |
| `argocd_chart_version` | `10.4.0` | Helm Chart 버전이며 Argo CD 애플리케이션 버전과 구분 |
| `argocd_kubeconfig` | `/home/devops/.kube/config` | 대상 클러스터 접근 설정 |
| `argocd_application_name` | `neuroplan-login-mvp` | Application 이름 |
| `argocd_project` | `default` | Argo CD Project |
| `argocd_destination_namespace` | `application` | 애플리케이션 배포 Namespace |
| `argocd_automated_sync` | `false` | 자동 동기화 활성화 여부 |

### 외부에서 제공해야 하는 변수

다음 변수는 Role 기본값에 없으므로 인벤토리 변수 또는 Playbook에서 지정합니다.

| 변수 | 프로젝트 설정 예시 |
|---|---|
| `application_github_owner` | `Infrastructure-hybrid09` |
| `application_github_repository` | `onprem-k8s-application-devopsVM` |
| `application_git_branch` | `main` |
| `application_kustomize_path` | `neuroplan-login-mvp/k8s/onprem` |

Application의 배포 대상 서버는 템플릿에서 `https://kubernetes.default.svc`로 지정됩니다.

## 실행 방법

저장소 루트에서 실행합니다.

### DevOps 환경과 함께 구성

```bash
ansible-playbook -i inventory/hosts.ini playbooks/devops.yml --ask-vault-pass
```

이 명령은 Argo CD뿐 아니라 Java, Maven, Node.js, Docker, Jenkins, Helm 등 Playbook에 포함된 다른 Role도 실행합니다.

### Argo CD Role만 사용하는 예시

필수 도구와 접근 권한이 준비되어 있다면 다음 Playbook으로 Role을 사용할 수 있습니다.

```yaml
---
- name: Configure Argo CD
  hosts: devops_nodes
  roles:
    - role: argocd
      vars:
        application_github_owner: Infrastructure-hybrid09
        application_github_repository: onprem-k8s-application-devopsVM
        application_git_branch: main
        application_kustomize_path: neuroplan-login-mvp/k8s/onprem
        argocd_automated_sync: false
```

Role의 Helm·kubectl·Manifest 생성 작업은 `become: false`로 실행되므로 접속 계정의 권한과 도구 경로를 확인합니다.

## 동기화 정책

최초 연결 시 기본값 `argocd_automated_sync: false`에서는 Application 템플릿에 자동 동기화 정책을 포함하지 않습니다.

자동 동기화를 활성화하려면 변수를 `true`로 설정하고 Role을 다시 실행합니다. 이때 다음 정책이 함께 적용됩니다.

- `prune: true`: Git에서 제거된 관리 대상 리소스를 클러스터에서도 삭제
- `selfHeal: true`: 클러스터의 수동 변경을 Git에 정의된 상태로 복구

최초 수동 동기화에는 별도 Playbook을 사용할 수 있습니다.

```bash
ansible-playbook -i inventory/hosts.ini playbooks/argocd-sync.yml --ask-vault-pass
```

[argocd-sync.yml](../../playbooks/argocd-sync.yml)은 `neuroplan-login-mvp` Application에 `prune: false`인 일회성 Sync를 요청하고 `Synced:Healthy` 상태를 기다립니다. 이 Playbook의 Application 이름, Namespace 및 kubeconfig 경로는 고정되어 있습니다.

**이 Role은 폴링 주기, Webhook, Git 저장소 인증을 설정하지 않습니다.** 운영 환경의 2분 폴링 설정은 이 Role의 구현 범위와 구분합니다.

## Check Mode 및 검증 범위

`--check` 실행 시 Helm 저장소 등록·갱신, 설치·업그레이드, Manifest 파일 생성 및 Application 적용은 건너뜁니다.

Helm 조회 명령은 실행되며, 기존 Release가 있으면 rollout 상태와 Pod·Application을 조회합니다. Application 템플릿은 파일 생성 없이 렌더링하므로 관련 변수가 필요합니다. 이는 Kubernetes API 수준의 Manifest 유효성 검증이나 애플리케이션 동작 검증을 의미하지 않습니다.

일반 실행 후 다음 명령으로 상태를 확인할 수 있습니다.

```bash
kubectl --kubeconfig=/home/devops/.kube/config get pods -n argocd
kubectl --kubeconfig=/home/devops/.kube/config get application neuroplan-login-mvp -n argocd
```

이 Role은 Application 상태를 조회하지만, 애플리케이션이 `Synced / Healthy`가 될 때까지 기다리지는 않습니다. 해당 대기는 별도 최초 동기화 Playbook에서 수행합니다.

현재 설치·업그레이드 판단 기준은 **Release 존재 여부와 Chart 버전**입니다. 최종 요약의 `SYNCHRONIZED`는 이 비교 결과이며 Application의 Sync 상태와 다릅니다. 요약에는 설치 전 조회값이 사용되므로 설치 후 실제 상태는 Helm·kubectl 조회 결과로 확인합니다.

## 관련 코드

- [Jenkins CI 파이프라인](https://github.com/Infrastructure-hybrid09/onprem-k8s-application-devopsVM/blob/main/Jenkinsfile)
- [On-Prem Kustomize 배포 설정](https://github.com/Infrastructure-hybrid09/onprem-k8s-application-devopsVM/tree/main/neuroplan-login-mvp/k8s/onprem)
- [전체 CI/CD 구성 Playbook](../../playbooks/cicd.yml)
- [DR 클러스터 등록 Role](../argocd_dr_cluster)
- [DR Application 구성 Role](../argocd_dr_application)

DR 클러스터 등록 및 DR Application 구성은 위의 별도 Role에서 관리합니다.

