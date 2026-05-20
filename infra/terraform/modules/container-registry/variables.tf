variable "project_name" {
  type = string
}

variable "compartment_id" {
  type = string
}

variable "ocir_endpoint" {
  type = string
}

variable "namespace" {
  type = string
}

variable "is_public" {
  type    = bool
  default = false
}

variable "is_immutable" {
  type    = bool
  default = false
}

variable "freeform_tags" {
  type    = map(string)
  default = {}
}
