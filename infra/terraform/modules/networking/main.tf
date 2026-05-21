resource "oci_core_vcn" "this" {
  compartment_id = var.compartment_id
  display_name   = "${var.project_name}-vcn"
  cidr_blocks    = [var.vcn_cidr]
  dns_label      = substr(replace(var.project_name, "-", ""), 0, 15)
  freeform_tags  = var.freeform_tags
}

resource "oci_core_internet_gateway" "this" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.this.id
  display_name   = "${var.project_name}-igw"
  enabled        = true
  freeform_tags  = var.freeform_tags
}

resource "oci_core_route_table" "public" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.this.id
  display_name   = "${var.project_name}-public-rt"
  freeform_tags  = var.freeform_tags

  route_rules {
    description       = "Demo internet egress"
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.this.id
  }
}

resource "oci_core_security_list" "demo_open" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.this.id
  display_name   = "${var.project_name}-demo-open-sl"
  freeform_tags  = var.freeform_tags

  ingress_security_rules {
    description = "DEMO ONLY: allow all inbound IPv4 traffic"
    protocol    = "all"
    source      = "0.0.0.0/0"
  }

  egress_security_rules {
    description = "DEMO ONLY: allow all outbound IPv4 traffic"
    protocol    = "all"
    destination = "0.0.0.0/0"
  }
}

resource "oci_core_subnet" "control_plane" {
  compartment_id             = var.compartment_id
  vcn_id                     = oci_core_vcn.this.id
  cidr_block                 = var.control_plane_subnet_cidr
  display_name               = "${var.project_name}-control-plane-subnet"
  dns_label                  = "cp"
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_security_list.demo_open.id]
  prohibit_public_ip_on_vnic = false
  freeform_tags              = var.freeform_tags
}

resource "oci_core_subnet" "workers" {
  compartment_id             = var.compartment_id
  vcn_id                     = oci_core_vcn.this.id
  cidr_block                 = var.worker_subnet_cidr
  display_name               = "${var.project_name}-workers-subnet"
  dns_label                  = "workers"
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_security_list.demo_open.id]
  prohibit_public_ip_on_vnic = false
  freeform_tags              = var.freeform_tags
}

resource "oci_core_subnet" "load_balancers" {
  compartment_id             = var.compartment_id
  vcn_id                     = oci_core_vcn.this.id
  cidr_block                 = var.lb_subnet_cidr
  display_name               = "${var.project_name}-lb-subnet"
  dns_label                  = "lb"
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_security_list.demo_open.id]
  prohibit_public_ip_on_vnic = false
  freeform_tags              = var.freeform_tags
}
