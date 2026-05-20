project_name     = "oracle-ddd-demo"
region           = "sa-saopaulo-1"
tenancy_ocid     = "ocid1.tenancy.oc1..aaaaaaaajknfkhizm335cuwwbrjf6grlsj2bxkuguicqradutzktix4ffdsa"
compartment_ocid = "ocid1.compartment.oc1..aaaaaaaanst7tfpmqfjl3q3kcnwkm3bdemblzj4snl3t4s72com5hrdt6qya"

auth                = "APIKey"
user_ocid           = "ocid1.user.oc1..aaaaaaaarc2glfbpc3r73hqvix2k56ctoebv6p5vzdbetrsx2w55vamndjhq"
fingerprint         = "36:f4:92:51:fa:48:c7:5b:7a:66:07:17:2d:76:aa:9f"
private_key_path    = "~/.oci/oci_api_key.pem"
config_file_profile = "DEFAULT"

vcn_cidr                  = "10.0.0.0/16"
control_plane_subnet_cidr = "10.0.0.0/24"
worker_subnet_cidr        = "10.0.10.0/24"
lb_subnet_cidr            = "10.0.20.0/24"

# Leave empty to use the first OKE version returned by OCI. Pin it for repeatability.
kubernetes_version = ""

node_count         = 2
node_shape         = "VM.Standard.E3.Flex"
node_ocpus         = 2
node_memory_in_gbs = 16

# Optional. Leave empty to auto-select from OCI node pool options.
node_image_id          = ""
node_image_name_filter = ""
ssh_public_key         = ""

freeform_tags = {
  environment = "demo"
}
