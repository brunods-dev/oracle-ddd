provider "oci" {
  region               = var.region
  auth                 = var.auth
  tenancy_ocid         = var.tenancy_ocid
  user_ocid            = var.user_ocid
  fingerprint          = var.fingerprint
  private_key_path     = var.private_key_path
  private_key_password = var.private_key_password
  config_file_profile  = var.config_file_profile
}
