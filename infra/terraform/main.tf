data "oci_identity_availability_domains" "ads" {
  compartment_id = var.tenancy_ocid
}

data "oci_identity_regions" "all" {}

data "oci_objectstorage_namespace" "this" {
  compartment_id = var.compartment_ocid
}

locals {
  matching_region_keys = [
    for region in data.oci_identity_regions.all.regions : lower(region.key)
    if lower(region.name) == lower(var.region)
  ]

  ocir_region_key = length(local.matching_region_keys) > 0 ? local.matching_region_keys[0] : lower(substr(var.region, 0, 3))
  ocir_endpoint   = "${local.ocir_region_key}.ocir.io"
}

module "networking" {
  source = "./modules/networking"

  project_name              = var.project_name
  compartment_id            = var.compartment_ocid
  vcn_cidr                  = var.vcn_cidr
  control_plane_subnet_cidr = var.control_plane_subnet_cidr
  worker_subnet_cidr        = var.worker_subnet_cidr
  lb_subnet_cidr            = var.lb_subnet_cidr
  freeform_tags             = local.common_tags
}

module "db" {
  source = "./modules/db"

  project_name        = var.project_name
  compartment_id      = var.compartment_ocid
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name

  subnet_id     = module.networking.worker_subnet_id
  nlb_subnet_id = module.networking.lb_subnet_id

  mysql_admin_username          = var.mysql_admin_username
  mysql_admin_password          = var.mysql_admin_password
  mysql_shape_name              = var.mysql_shape_name
  mysql_data_storage_size_in_gb = var.mysql_data_storage_size_in_gb
  mysql_auto_expand_storage     = var.mysql_auto_expand_storage
  mysql_max_storage_size_in_gbs = var.mysql_max_storage_size_in_gbs
  mysql_port                    = var.mysql_port
  mysql_port_x                  = var.mysql_port_x
  mysql_database_name           = var.mysql_database_name
  mysql_enable_heatwave_cluster = var.mysql_enable_heatwave_cluster
  mysql_heatwave_shape_name     = var.mysql_heatwave_shape_name
  mysql_heatwave_cluster_size   = var.mysql_heatwave_cluster_size

  freeform_tags = local.common_tags

  depends_on = [
    module.networking
  ]
}

module "container_registry" {
  source = "./modules/container-registry"

  project_name   = var.project_name
  compartment_id = var.compartment_ocid
  ocir_endpoint  = local.ocir_endpoint
  namespace      = data.oci_objectstorage_namespace.this.namespace
  is_public      = false
  is_immutable   = false
  freeform_tags  = local.common_tags
}

module "oke" {
  source = "./modules/oke"

  project_name               = var.project_name
  compartment_id             = var.compartment_ocid
  vcn_id                     = module.networking.vcn_id
  control_plane_subnet_id    = module.networking.control_plane_subnet_id
  worker_subnet_id           = module.networking.worker_subnet_id
  lb_subnet_id               = module.networking.lb_subnet_id
  availability_domains       = data.oci_identity_availability_domains.ads.availability_domains
  kubernetes_version         = var.kubernetes_version
  node_count                 = var.node_count
  node_shape                 = var.node_shape
  node_ocpus                 = var.node_ocpus
  node_memory_in_gbs         = var.node_memory_in_gbs
  node_image_id              = var.node_image_id
  node_image_name_filter     = var.node_image_name_filter
  node_pool_os_type          = var.node_pool_os_type
  node_pool_os_arch_override = var.node_pool_os_arch_override
  ssh_public_key             = var.ssh_public_key
  pods_cidr                  = var.pods_cidr
  services_cidr              = var.services_cidr
  freeform_tags              = local.common_tags
}

data "oci_containerengine_cluster_kube_config" "this" {
  cluster_id    = module.oke.cluster_id
  token_version = "2.0.0"
}
