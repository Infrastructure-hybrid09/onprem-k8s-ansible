import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey.DirectEntryPrivateKeySource


def store = SystemCredentialsProvider.getInstance().getStore()
def domain = Domain.global()

def changed = false


// =====================================================
// Application Repository SSH Credential
// - 실제 팀 App Repository newTag commit/push용
// =====================================================

def appRepoCredentialId = '$app_repo_credential_id'
def appRepoPrivateKey   = '''$app_repo_private_key'''
def appRepoDescription  = 'GitHub NeuroPlan Application Repository Deploy Key'

def existingAppRepo = store.getCredentials(domain).find {
    it.id == appRepoCredentialId
}

def newAppRepoCredential = new BasicSSHUserPrivateKey(
    CredentialsScope.GLOBAL,
    appRepoCredentialId,
    'git',
    new DirectEntryPrivateKeySource(appRepoPrivateKey),
    '',
    appRepoDescription
)

def existingAppRepoPrivateKey = ''

if (existingAppRepo instanceof BasicSSHUserPrivateKey) {

    def keys = existingAppRepo.getPrivateKeys()

    if (keys != null && !keys.isEmpty()) {
        existingAppRepoPrivateKey = keys[0].trim()
    }
}

def appRepoSame =
    existingAppRepo instanceof BasicSSHUserPrivateKey &&
    existingAppRepo.username == 'git' &&
    existingAppRepoPrivateKey == appRepoPrivateKey.trim() &&
    existingAppRepo.description == appRepoDescription

if (!appRepoSame) {

    if (existingAppRepo != null) {
        store.updateCredentials(
            domain,
            existingAppRepo,
            newAppRepoCredential
        )
    } else {
        store.addCredentials(
            domain,
            newAppRepoCredential
        )
    }

    changed = true
}



// =====================================================
// Harbor Registry Credential
// - Jenkins image push/pull용 Robot Account
// =====================================================

def harborCredentialId = '$harbor_credential_id'
def harborUsername     = '$harbor_username'
def harborSecret       = '''$harbor_secret'''
def harborDescription  = 'Harbor Registry Robot Account'

def existingHarbor = store.getCredentials(domain).find {
    it.id == harborCredentialId
}

def newHarborCredential = new UsernamePasswordCredentialsImpl(
    CredentialsScope.GLOBAL,
    harborCredentialId,
    harborDescription,
    harborUsername,
    harborSecret
)

def harborSame =
    existingHarbor instanceof UsernamePasswordCredentialsImpl &&
    existingHarbor.username == harborUsername &&
    existingHarbor.password.plainText == harborSecret &&
    existingHarbor.description == harborDescription

if (!harborSame) {

    if (existingHarbor != null) {
        store.updateCredentials(
            domain,
            existingHarbor,
            newHarborCredential
        )
    } else {
        store.addCredentials(
            domain,
            newHarborCredential
        )
    }

    changed = true
}

store.save()

println(changed ? 'CHANGED' : 'UNCHANGED')
