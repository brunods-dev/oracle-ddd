output "vcn_id" {
  value = oci_core_vcn.this.id
}

output "internet_gateway_id" {
  value = oci_core_internet_gateway.this.id
}

output "route_table_id" {
  value = oci_core_route_table.public.id
}

output "security_list_id" {
  value = oci_core_security_list.demo_open.id
}

output "control_plane_subnet_id" {
  value = oci_core_subnet.control_plane.id
}

output "worker_subnet_id" {
  value = oci_core_subnet.workers.id
}

output "lb_subnet_id" {
  value = oci_core_subnet.load_balancers.id
}

output "subnet_ids" {
  value = {
    control_plane  = oci_core_subnet.control_plane.id
    workers        = oci_core_subnet.workers.id
    load_balancers = oci_core_subnet.load_balancers.id
  }
}
