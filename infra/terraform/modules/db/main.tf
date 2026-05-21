data "oci_mysql_shapes" "selected" {
  compartment_id      = var.compartment_id
  availability_domain = var.availability_domain
  name                = var.mysql_shape_name
}

locals {
  mysql_hostname_label = substr(replace("${var.project_name}-mysql", "-", ""), 0, 30)
}

resource "oci_mysql_mysql_db_system" "this" {
  availability_domain     = var.availability_domain
  compartment_id          = var.compartment_id
  display_name            = "${var.project_name}-mysql"
  description             = "Demo MySQL HeatWave DB System for ${var.project_name}"
  shape_name              = var.mysql_shape_name
  subnet_id               = var.subnet_id
  hostname_label          = local.mysql_hostname_label
  admin_username          = var.mysql_admin_username
  admin_password          = var.mysql_admin_password
  data_storage_size_in_gb = var.mysql_data_storage_size_in_gb
  port                    = var.mysql_port
  port_x                  = var.mysql_port_x
  access_mode             = "UNRESTRICTED"
  database_mode           = "READ_WRITE"
  is_highly_available     = false
  freeform_tags           = var.freeform_tags

  data_storage {
    is_auto_expand_storage_enabled = var.mysql_auto_expand_storage
    max_storage_size_in_gbs        = var.mysql_max_storage_size_in_gbs
  }

  backup_policy {
    is_enabled        = false
    retention_in_days = 1

    pitr_policy {
      is_enabled = false
    }
  }

  deletion_policy {
    final_backup        = "SKIP_FINAL_BACKUP"
    is_delete_protected = false
  }

  maintenance {
    window_start_time = "sun 03:00"
  }

  lifecycle {
    precondition {
      condition     = length(data.oci_mysql_shapes.selected.shapes) > 0
      error_message = "The configured MySQL shape was not found in this region/availability domain: ${var.mysql_shape_name}."
    }
  }
}

resource "oci_mysql_heat_wave_cluster" "this" {
  count = var.mysql_enable_heatwave_cluster ? 1 : 0

  db_system_id         = oci_mysql_mysql_db_system.this.id
  cluster_size         = var.mysql_heatwave_cluster_size
  shape_name           = var.mysql_heatwave_shape_name
  is_lakehouse_enabled = false

  depends_on = [
    oci_mysql_mysql_db_system.this
  ]
}

locals {
  mysql_private_ip       = try(oci_mysql_mysql_db_system.this.endpoints[0].ip_address, oci_mysql_mysql_db_system.this.ip_address)
  mysql_private_hostname = try(oci_mysql_mysql_db_system.this.endpoints[0].hostname, "")
}

resource "oci_network_load_balancer_network_load_balancer" "mysql_public" {
  compartment_id = var.compartment_id
  display_name   = "${var.project_name}-mysql-public-nlb"
  subnet_id      = var.nlb_subnet_id
  is_private     = false
  freeform_tags  = var.freeform_tags
}

resource "oci_network_load_balancer_backend_set" "mysql" {
  network_load_balancer_id = oci_network_load_balancer_network_load_balancer.mysql_public.id
  name                     = "mysql_backend_set"
  policy                   = "FIVE_TUPLE"
  is_preserve_source       = false

  health_checker {
    protocol           = "TCP"
    port               = var.mysql_port
    interval_in_millis = 10000
    timeout_in_millis  = 3000
    retries            = 3
  }
}

resource "oci_network_load_balancer_backend" "mysql" {
  network_load_balancer_id = oci_network_load_balancer_network_load_balancer.mysql_public.id
  backend_set_name         = oci_network_load_balancer_backend_set.mysql.name
  ip_address               = local.mysql_private_ip
  port                     = var.mysql_port
  weight                   = 1
  is_backup                = false
  is_drain                 = false
  is_offline               = false

  depends_on = [
    oci_mysql_mysql_db_system.this,
    oci_network_load_balancer_backend_set.mysql
  ]
}

resource "oci_network_load_balancer_listener" "mysql" {
  network_load_balancer_id = oci_network_load_balancer_network_load_balancer.mysql_public.id
  name                     = "mysql_tcp_3306"
  default_backend_set_name = oci_network_load_balancer_backend_set.mysql.name
  port                     = var.mysql_port
  protocol                 = "TCP"
  tcp_idle_timeout         = 300

  depends_on = [
    oci_network_load_balancer_backend.mysql
  ]
}