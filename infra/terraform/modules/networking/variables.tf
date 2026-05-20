variable "project_name" {
  type = string
}

variable "compartment_id" {
  type = string
}

variable "vcn_cidr" {
  type = string
}

variable "control_plane_subnet_cidr" {
  type = string
}

variable "worker_subnet_cidr" {
  type = string
}

variable "lb_subnet_cidr" {
  type = string
}

variable "freeform_tags" {
  type    = map(string)
  default = {}
}
