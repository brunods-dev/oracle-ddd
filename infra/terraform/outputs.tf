output "project_name" {
  value = var.project_name
}

output "compartment_id" {
  value = var.compartment_ocid
}

output "region" {
  value = var.region
}

output "ocir_endpoint" {
  value = module.container_registry.ocir_endpoint
}

output "ocir_namespace" {
  value = module.container_registry.ocir_namespace
}

output "backend_repository_url" {
  value = module.container_registry.backend_repository_url
}

output "frontend_repository_url" {
  value = module.container_registry.frontend_repository_url
}

output "vcn_id" {
  value = module.networking.vcn_id
}

output "subnet_ids" {
  value = module.networking.subnet_ids
}

output "control_plane_subnet_id" {
  value = module.networking.control_plane_subnet_id
}

output "worker_subnet_id" {
  value = module.networking.worker_subnet_id
}

output "lb_subnet_id" {
  value = module.networking.lb_subnet_id
}

output "cluster_id" {
  value = module.oke.cluster_id
}

output "cluster_name" {
  value = module.oke.cluster_name
}

output "cluster_endpoints" {
  value = module.oke.cluster_endpoints
}

output "kubernetes_version" {
  value = module.oke.kubernetes_version
}

output "node_pool_id" {
  value = module.oke.node_pool_id
}

output "selected_node_image_id" {
  value = module.oke.selected_node_image_id
}

output "kubeconfig" {
  value     = data.oci_containerengine_cluster_kube_config.this.content
  sensitive = true
}

output "available_kubernetes_versions" {
  value = module.oke.available_kubernetes_versions
}

output "latest_kubernetes_version" {
  value = module.oke.latest_kubernetes_version
}

output "node_shape" {
  value = module.oke.node_shape
}

output "node_shape_processor_description" {
  value = module.oke.node_shape_processor_description
}

output "node_pool_os_arch" {
  value = module.oke.node_pool_os_arch
}

output "node_pool_os_type" {
  value = module.oke.node_pool_os_type
}

output "worker_node_container_platform" {
  value = module.oke.worker_node_container_platform
}

output "selected_node_image_name" {
  value = module.oke.selected_node_image_name
}

output "mysql_db_system_id" {
  value = module.db.mysql_db_system_id
}

output "mysql_db_system_state" {
  value = module.db.mysql_db_system_state
}

output "mysql_private_ip" {
  value = module.db.mysql_private_ip
}

output "mysql_private_hostname" {
  value = module.db.mysql_private_hostname
}

output "mysql_port" {
  value = module.db.mysql_port
}

output "mysql_database_name" {
  value = module.db.mysql_database_name
}

output "mysql_public_ip" {
  value = module.db.mysql_public_ip
}

output "mysql_public_endpoint" {
  value = module.db.mysql_public_endpoint
}

output "mysql_public_jdbc_url" {
  value = module.db.mysql_public_jdbc_url
}

output "mysql_private_jdbc_url" {
  value = module.db.mysql_private_jdbc_url
}

output "mysql_heatwave_cluster_id" {
  value = module.db.mysql_heatwave_cluster_id
}

output "mysql_heatwave_cluster_state" {
  value = module.db.mysql_heatwave_cluster_state
}