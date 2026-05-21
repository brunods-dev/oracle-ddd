locals {
  mysql_public_ip_candidates = [
    for ip in oci_network_load_balancer_network_load_balancer.mysql_public.ip_addresses :
    ip.ip_address
    if ip.is_public
  ]

  mysql_public_ip = length(local.mysql_public_ip_candidates) > 0 ? local.mysql_public_ip_candidates[0] : ""
}

output "mysql_db_system_id" {
  value = oci_mysql_mysql_db_system.this.id
}

output "mysql_db_system_state" {
  value = oci_mysql_mysql_db_system.this.state
}

output "mysql_private_ip" {
  value = local.mysql_private_ip
}

output "mysql_private_hostname" {
  value = local.mysql_private_hostname
}

output "mysql_port" {
  value = var.mysql_port
}

output "mysql_database_name" {
  value = var.mysql_database_name
}

output "mysql_public_nlb_id" {
  value = oci_network_load_balancer_network_load_balancer.mysql_public.id
}

output "mysql_public_ip" {
  value = local.mysql_public_ip
}

output "mysql_public_endpoint" {
  value = "${local.mysql_public_ip}:${var.mysql_port}"
}

output "mysql_private_jdbc_url" {
  value = "jdbc:mysql://${local.mysql_private_ip}:${var.mysql_port}/${var.mysql_database_name}?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
}

output "mysql_public_jdbc_url" {
  value = "jdbc:mysql://${local.mysql_public_ip}:${var.mysql_port}/${var.mysql_database_name}?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
}

output "mysql_heatwave_cluster_id" {
  value = var.mysql_enable_heatwave_cluster ? oci_mysql_heat_wave_cluster.this[0].id : null
}

output "mysql_heatwave_cluster_state" {
  value = var.mysql_enable_heatwave_cluster ? oci_mysql_heat_wave_cluster.this[0].state : null
}