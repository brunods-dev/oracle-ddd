# Terraform loads this file automatically, with higher precedence than
# TF_VAR_* environment variables.
#
# Keep environment-specific values in ../.env so infra/scripts/01_apply_terraform.sh
# can source them and Terraform can read them from TF_VAR_*.
#
# Example:
#   export TF_VAR_region="sa-saopaulo-1"
#   export TF_VAR_tenancy_ocid="ocid1.tenancy.oc1..example"
#   export TF_VAR_compartment_ocid="ocid1.compartment.oc1..example"
#
# Put values here only when you intentionally want to override ../.env.
