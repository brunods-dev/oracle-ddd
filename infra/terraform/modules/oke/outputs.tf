output "cluster_id" {
  value = oci_containerengine_cluster.this.id
}

output "cluster_name" {
  value = oci_containerengine_cluster.this.name
}

output "cluster_endpoints" {
  value = oci_containerengine_cluster.this.endpoints
}

output "kubernetes_version" {
  value = local.effective_kubernetes_version
}

output "node_pool_id" {
  value = oci_containerengine_node_pool.workers.id
}

output "selected_node_image_id" {
  value = local.selected_node_image_id
}

output "worker_subnet_id" {
  value = var.worker_subnet_id
}

output "load_balancer_subnet_id" {
  value = var.lb_subnet_id
}

output "available_kubernetes_versions" {
  value = data.oci_containerengine_cluster_option.all.kubernetes_versions
}

output "latest_kubernetes_version" {
  value = local.latest_kubernetes_version
}

output "node_shape" {
  value = var.node_shape
}

output "node_shape_processor_description" {
  value = local.selected_node_shape_processor_description
}

output "node_pool_os_arch" {
  value = local.effective_node_pool_os_arch
}

output "node_pool_os_type" {
  value = var.node_pool_os_type
}

output "worker_node_container_platform" {
  value = local.worker_node_container_platform
}

output "selected_node_image_name" {
  value = local.selected_node_image_name
}
